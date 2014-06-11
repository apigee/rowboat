/*
 * Copyright 2014 Apigee Corporation.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.apigee.rowboat.handles;

import io.apigee.rowboat.NetworkPolicy;
import io.apigee.rowboat.NodeRuntime;
import io.apigee.rowboat.internal.NodeOSException;
import io.apigee.rowboat.internal.SelectorHandler;
import io.apigee.rowboat.internal.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.BindException;
import java.net.ConnectException;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Node's own script modules use this internal module to implement the guts of async TCP.
 */
public class NIOSocketHandle
    extends AbstractHandle
{
    private static final Logger log = LoggerFactory.getLogger(NIOSocketHandle.class);

    public static final int    READ_BUFFER_SIZE = 32767;

    private InetSocketAddress       boundAddress;
    private ServerSocketChannel     svrChannel;
    private SocketChannel           clientChannel;
    private SelectionKey            selKey;
    private boolean                 readStarted;
    private ArrayDeque<QueuedWrite> writeQueue;
    private int                     queuedBytes;
    private ByteBuffer              readBuffer;
    private boolean                 writeReady;
    private ReadCompleteCallback    onRead;
    private Object                  onReadCtx;
    private Consumer<AbstractHandle> onConnection;
    private Consumer<Object>        onConnectComplete;

    public NIOSocketHandle(NodeRuntime runtime)
    {
        super(runtime);
    }

    public NIOSocketHandle(NodeRuntime runtime, SocketChannel clientChannel)
        throws IOException
    {
        super(runtime);
        this.clientChannel = clientChannel;
        clientInit();
        selKey = clientChannel.register(runtime.getSelector(), SelectionKey.OP_WRITE,
                                        new SelectorHandler()
                                        {
                                            @Override
                                            public void selected(SelectionKey key)
                                            {
                                                clientSelected(key);
                                            }
                                        });
    }

    private void clientInit()
        throws IOException
    {
        writeQueue = new ArrayDeque<>();
        readBuffer = ByteBuffer.allocate(READ_BUFFER_SIZE);
        clientChannel.configureBlocking(false);
        setNoDelay(true);
    }

    private void addInterest(int i)
    {
        selKey.interestOps(selKey.interestOps() | i);
        if (log.isDebugEnabled()) {
            log.debug("Interest now {}", selKey.interestOps());
        }
    }

    private void removeInterest(int i)
    {
        if (selKey.isValid()) {
            selKey.interestOps(selKey.interestOps() & ~i);
            if (log.isDebugEnabled()) {
                log.debug("Interest now {}", selKey.interestOps());
            }
        }
    }

    @Override
    public void close()
    {
        try {
            if (clientChannel != null) {
                if (log.isDebugEnabled()) {
                    log.debug("Closing client channel {}", clientChannel);
                }
                clientChannel.close();
                runtime.unregisterCloseable(clientChannel);
            }
            if (svrChannel != null) {
                if (log.isDebugEnabled()) {
                    log.debug("Closing server channel {}", svrChannel);
                }
                svrChannel.close();
                runtime.unregisterCloseable(svrChannel);
            }
        } catch (IOException ioe) {
            log.debug("Uncaught exception in channel close: {}", ioe);
        }
    }

    public void bind(String address, int port)
        throws NodeOSException
    {
        boundAddress = new InetSocketAddress(address, port);
        if (boundAddress.isUnresolved()) {
            throw new NodeOSException(Constants.ENOENT);
        }
    }

    public void listen(int backlog, Consumer<AbstractHandle> cb)
        throws NodeOSException
    {
        if (boundAddress == null) {
            throw new NodeOSException(Constants.EINVAL);
        }
        NetworkPolicy netPolicy = getNetworkPolicy();
        if ((netPolicy != null) && !netPolicy.allowListening(boundAddress)) {
            log.debug("Address {} not allowed by network policy", boundAddress);
            throw new NodeOSException(Constants.EINVAL);
        }

        this.onConnection = cb;
        if (log.isDebugEnabled()) {
            log.debug("Server listening on {} with backlog {}",
                      boundAddress, backlog);
        }

        boolean success = false;
        try {
            svrChannel = ServerSocketChannel.open();
            runtime.registerCloseable(svrChannel);
            svrChannel.configureBlocking(false);
            svrChannel.socket().setReuseAddress(true);
            svrChannel.socket().bind(boundAddress, backlog);
            svrChannel.register(runtime.getSelector(), SelectionKey.OP_ACCEPT,
                                new SelectorHandler()
                                {
                                    @Override
                                    public void selected(SelectionKey key)
                                    {
                                        serverSelected(key);
                                    }
                                });
            success = true;

        } catch (BindException be) {
            log.debug("Error listening: {}", be);
            throw new NodeOSException(Constants.EADDRINUSE);
        } catch (IOException ioe) {
            log.debug("Error listening: {}", ioe);
            throw new NodeOSException(Constants.EIO);
        } finally {
            if (!success && (svrChannel != null)) {
                runtime.unregisterCloseable(svrChannel);
                try {
                    svrChannel.close();
                } catch (IOException ioe) {
                    log.debug("Error closing channel that might be closed: {}", ioe);
                }
            }
        }
    }

    protected void serverSelected(SelectionKey key)
    {
        if (!key.isValid()) {
            return;
        }
        if (log.isDebugEnabled()) {
            log.debug("Server selected: a = {}", key.isAcceptable());
        }

        if (key.isAcceptable()) {
            SocketChannel child = null;
            do {
                try {
                    child = svrChannel.accept();
                    if (child != null) {
                        if (log.isDebugEnabled()) {
                            log.debug("Accepted new socket {}", child);
                        }

                        boolean success = false;
                        try {
                            runtime.registerCloseable(child);
                            NIOSocketHandle sock = new NIOSocketHandle(runtime, child);
                            onConnection.accept(sock);
                            success = true;
                        } finally {
                            if (!success) {
                                runtime.unregisterCloseable(child);
                                try {
                                    child.close();
                                } catch (IOException ioe) {
                                    log.debug("Error closing channel that might be closed: {}", ioe);
                                }
                            }
                        }
                    }
                } catch (ClosedChannelException cce) {
                    log.debug("Server channel has been closed");
                    break;
                } catch (IOException ioe) {
                    log.error("Error accepting a new socket: {}", ioe);
                }
            } while (child != null);
        }
    }

    @Override
    public int write(ByteBuffer buf, Object context, WriteCompleteCallback cb)
    {
        QueuedWrite qw = new QueuedWrite(buf, context, cb);
        offerWrite(qw);
        return qw.length;
    }

    public void shutdown(Object context, WriteCompleteCallback cb)
    {
        QueuedWrite qw = new QueuedWrite(null, context, cb);
        qw.shutdown = true;
        offerWrite(qw);
    }

    private void offerWrite(QueuedWrite qw)
    {
        if (writeQueue.isEmpty() && !qw.shutdown) {
            int written;
            try {
                written = clientChannel.write(qw.buf);
            } catch (IOException ioe) {
                // Hacky? We failed the immediate write, but the callback isn't set yet,
                // so go back and do it later
                queueWrite(qw);
                return;
            }
            if (log.isDebugEnabled()) {
                log.debug("Wrote {} to {} from {}", written, clientChannel, qw.buf);
            }
            if (qw.buf.hasRemaining()) {
                // We didn't write the whole thing.
                writeReady = false;
                queueWrite(qw);
            } else {
                qw.onComplete.complete(qw.context, null, true);
            }
        } else {
            queueWrite(qw);
        }
    }

    private void queueWrite(QueuedWrite qw)
    {
        addInterest(SelectionKey.OP_WRITE);
        writeQueue.addLast(qw);
        queuedBytes += qw.length;
    }

    @Override
    public int getWritesOutstanding()
    {
        return queuedBytes;
    }

    @Override
    public void startReading(Object context, ReadCompleteCallback cb)
    {
        if (!readStarted) {
            this.onRead = cb;
            this.onReadCtx = context;
            addInterest(SelectionKey.OP_READ);
            readStarted = true;
        }
    }

    @Override
    public void stopReading()
    {
        if (readStarted) {
            removeInterest(SelectionKey.OP_READ);
            readStarted = false;
        }
    }

    public void connect(String host, int port, Consumer<Object> cb)
        throws NodeOSException
    {
        boolean success = false;
        SocketChannel newChannel = null;
        try {
            InetSocketAddress targetAddress = new InetSocketAddress(host, port);
            NetworkPolicy netPolicy = getNetworkPolicy();
            if ((netPolicy != null) && !netPolicy.allowConnection(targetAddress)) {
                log.debug("Disallowed connection to {} due to network policy", targetAddress);
                throw new NodeOSException(Constants.EINVAL);
            }

            if (log.isDebugEnabled()) {
                log.debug("Client connecting to {}:{}", host, port);
            }
            if (boundAddress == null) {
                newChannel = SocketChannel.open();
            } else {
                newChannel = SocketChannel.open(boundAddress);
            }

            runtime.registerCloseable(newChannel);
            clientChannel = newChannel;
            clientInit();
            this.onConnectComplete = cb;
            newChannel.connect(targetAddress);
            selKey = newChannel.register(runtime.getSelector(),
                                                    SelectionKey.OP_CONNECT,
                                                    new SelectorHandler()
                                                    {
                                                        @Override
                                                        public void selected(SelectionKey key)
                                                        {
                                                            clientSelected(key);
                                                        }
                                                    });

            success = true;

        } catch (IOException ioe) {
            log.debug("Error on connect: {}", ioe);
            throw new NodeOSException(Constants.EIO);
        } finally {
            if (!success && (newChannel != null)) {
                runtime.unregisterCloseable(newChannel);
                try {
                    newChannel.close();
                } catch (IOException ioe) {
                    log.debug("Error closing channel that might be closed: {}", ioe);
                }
            }
        }
    }

    protected void clientSelected(SelectionKey key)
    {
        if (log.isDebugEnabled()) {
            log.debug("Client {} selected: interest = {} r = {} w = {} c = {}", clientChannel,
                      selKey.interestOps(), key.isReadable(), key.isWritable(), key.isConnectable());
        }

        if (key.isValid() && key.isConnectable()) {
            processConnect();
        }
        if (key.isValid() && (key.isWritable() || writeReady)) {
            processWrites();
        }
        if (key.isValid() && key.isReadable()) {
            processReads();
        }
    }

    private void processConnect()
    {
        try {
            removeInterest(SelectionKey.OP_CONNECT);
            addInterest(SelectionKey.OP_WRITE);
            clientChannel.finishConnect();
            if (log.isDebugEnabled()) {
                log.debug("Client {} connected", clientChannel);
            }
            // Must be zero, not anything else -- that's what net.js expects
            onConnectComplete.accept(0);

        } catch (ConnectException ce) {
            if (log.isDebugEnabled()) {
                log.debug("Error completing connect: {}", ce);
            }
            onConnectComplete.accept(Constants.ECONNREFUSED);

        } catch (IOException ioe) {
            if (log.isDebugEnabled()) {
                log.debug("Error completing connect: {}", ioe);
            }
             onConnectComplete.accept(Constants.EIO);
        }
    }

    private void processWrites()
    {
        writeReady = true;
        removeInterest(SelectionKey.OP_WRITE);
        QueuedWrite qw;
        while (true) {
            qw = writeQueue.pollFirst();
            if (qw == null) {
                break;
            }
            queuedBytes -= qw.length;
            assert(queuedBytes >= 0);
            try {
                if (qw.shutdown) {
                    if (log.isDebugEnabled()) {
                        log.debug("Sending shutdown for {}", clientChannel);
                    }
                    clientChannel.socket().shutdownOutput();
                    qw.onComplete.complete(qw.context, null, true);
                } else {
                    int written = clientChannel.write(qw.buf);
                    if (log.isDebugEnabled()) {
                        log.debug("Wrote {} to {} from {}", written, clientChannel, qw.buf);
                    }
                    if (qw.buf.hasRemaining()) {
                        // We didn't write the whole thing -- need to keep writing.
                        writeReady = false;
                        writeQueue.addFirst(qw);
                        queuedBytes += qw.length;
                        addInterest(SelectionKey.OP_WRITE);
                        break;
                    } else {
                        qw.onComplete.complete(qw.context, null, true);
                    }
                }

            } catch (ClosedChannelException cce) {
                if (log.isDebugEnabled()) {
                    log.debug("Channel is closed");
                }
                qw.onComplete.complete(qw.context, Constants.EOF, true);
            } catch (IOException ioe) {
                if (log.isDebugEnabled()) {
                    log.debug("Error on write: {}", ioe);
                }
                qw.onComplete.complete(qw.context, Constants.EIO, true);
            }
        }
    }

    private void processReads()
    {
        if (!readStarted) {
            return;
        }
        int read;
        do {
            try {
                read = clientChannel.read(readBuffer);
            } catch (IOException ioe) {
                if (log.isDebugEnabled()) {
                    log.debug("Error reading from channel: {}", ioe, ioe);
                }
                read = -1;
            }
            if (log.isDebugEnabled()) {
                log.debug("Read {} bytes from {} into {}", read, clientChannel, readBuffer);
            }
            if (read > 0) {
                readBuffer.flip();
                // Copy from the read buffer so that we can re use the big thing
                ByteBuffer buf = ByteBuffer.allocate(readBuffer.remaining());
                buf.put(readBuffer);
                buf.flip();
                readBuffer.clear();
                onRead.complete(onReadCtx, null, buf);

            } else if (read < 0) {
                removeInterest(SelectionKey.OP_READ);
                onRead.complete(onReadCtx, Constants.EOF, null);
            }
        } while (readStarted && (read > 0));
    }

    private Map<String, Object> formatAddress(InetSocketAddress addr)
    {
        HashMap<String, Object> ret = new HashMap<>();
        ret.put("port", addr.getPort());
        ret.put("address", addr.getAddress().getHostAddress());
        if (addr.getAddress() instanceof Inet6Address) {
            ret.put("family", "IPv6");
        } else {
            ret.put("family", "IPv4");
        }
        return ret;
    }

    public Map<String, Object> getSockName()
    {
        InetSocketAddress addr;
        if (svrChannel == null) {
            addr  = (InetSocketAddress)(clientChannel.socket().getLocalSocketAddress());
        } else {
            addr = (InetSocketAddress)(svrChannel.socket().getLocalSocketAddress());
        }
        return (addr == null ? null : formatAddress(addr));
    }

    public Map<String, Object> getPeerName()
    {
        if (clientChannel == null) {
            return null;
        }
        InetSocketAddress addr = (InetSocketAddress)(clientChannel.socket().getRemoteSocketAddress());
        return (addr == null ? null : formatAddress(addr));
    }

    public void setNoDelay(boolean nd)
        throws NodeOSException
    {
        if (clientChannel != null) {
            try {
                clientChannel.socket().setTcpNoDelay(nd);
            } catch (SocketException e) {
                log.error("Error setting TCP no delay on {}: {}", this, e);
                throw new NodeOSException(Constants.EIO);
            }
        }
    }

    public void setKeepAlive(boolean nd)
        throws NodeOSException
    {
        if (clientChannel != null) {
            try {
                clientChannel.socket().setKeepAlive(nd);
            } catch (SocketException e) {
                log.error("Error setting TCP keep alive on {}: {}", this, e);
                throw new NodeOSException(Constants.EIO);
            }
        }
    }

    private NetworkPolicy getNetworkPolicy()
    {
        if (runtime.getSandbox() == null) {
            return null;
        }
        return runtime.getSandbox().getNetworkPolicy();
    }

    public static class QueuedWrite
    {
        ByteBuffer buf;
        int length;
        Object context;
        WriteCompleteCallback onComplete;
        boolean shutdown;

        public QueuedWrite(ByteBuffer buf, Object context, WriteCompleteCallback onComplete)
        {
            this.buf = buf;
            this.length = (buf == null ? 0 : buf.remaining());
            this.onComplete = onComplete;
            this.context = context;
        }
    }
}

