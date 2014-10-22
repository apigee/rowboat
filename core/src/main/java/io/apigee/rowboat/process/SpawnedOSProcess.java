package io.apigee.rowboat.process;

import io.apigee.rowboat.internal.ScriptRunner;
import io.apigee.trireme.kernel.ErrorCodes;
import io.apigee.trireme.kernel.OSException;
import io.apigee.trireme.kernel.handles.AbstractHandle;
import io.apigee.trireme.kernel.handles.JavaInputStreamHandle;
import io.apigee.trireme.kernel.handles.JavaOutputStreamHandle;
import io.apigee.trireme.kernel.streams.BitBucketOutputStream;
import io.apigee.trireme.kernel.streams.StreamPiper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.IntConsumer;

public class SpawnedOSProcess
{
    private static final Logger log = LoggerFactory.getLogger(SpawnedOSProcess.class);

    private final ScriptRunner runtime;

    private java.lang.Process proc;
    private boolean finished;

    @SuppressWarnings("unused")
    public SpawnedOSProcess(ScriptRunner runtime)
    {
        this.runtime = runtime;
    }

    @SuppressWarnings("unused")
    public void terminate(String signal)
    {
        if (proc != null) {
            proc.destroy();
        }
    }

    @SuppressWarnings("unused")
    public void close()
    {
        if (!finished) {
            finished = true;
            terminate("0");
        }
    }

    /**
     * Set "socket" to a writable stream that can write to the output stream "out".
     * This will be used for stdin.
     */
    private AbstractHandle createOutputStream(ProcessInfo.StdioType type, int fd,
                                              OutputStream out)
        throws OSException
    {
        AbstractHandle ret = null;
        switch (type) {
        case PIPE:
            // Create a new handle that writes to the output stream.
            if (log.isDebugEnabled()) {
                log.debug("Setting up output stream {}", out);
            }
            ret = new JavaOutputStreamHandle(out);
            break;

        case IGNORE:
            // Close the stream and do nothing
            if (log.isDebugEnabled()) {
                log.debug("Ignoring output");
            }
            try {
                out.close();
            } catch (IOException ioe) {
                log.debug("Output.close() threw: {}", ioe);
            }
            break;

        case FD:
            // Assuming fd is zero, read from stdin and set that up.
            if (fd != 0) {
                throw new OSException(ErrorCodes.EINVAL, "Only FDs 0, 1, and 2 supported");
            }
            StreamPiper piper = new StreamPiper(runtime.getStdin(), out, false);
            piper.start(runtime.getUnboundedPool());
            break;

        default:
            throw new OSException(ErrorCodes.EINVAL, "Unsupported stdio type " + type);
        }
        return ret;
    }

    /**
     * Set "socket" to a readable stream that can read from the input stream "in".
     * This wil be used for stdout and stderr.
     */
    private AbstractHandle createInputStream(ProcessInfo.StdioType type, int fd,
                                             InputStream in)
        throws OSException
    {
        AbstractHandle ret = null;
        switch (type) {
        case PIPE:
            if (log.isDebugEnabled()) {
                log.debug("Setting up input stream {}", in);
            }
            ret = new JavaInputStreamHandle(in, runtime);
            break;

        case IGNORE:
            if (log.isDebugEnabled()) {
                log.debug("Setting up to discard all output");
            }
            StreamPiper ignorePiper = new StreamPiper(in, new BitBucketOutputStream(), false);
            ignorePiper.start(runtime.getUnboundedPool());
            break;

        case FD:
            StreamPiper piper;
            switch (fd) {
            case 1:
                piper = new StreamPiper(in, runtime.getStdout(), false);
                piper.start(runtime.getUnboundedPool());
                break;
            case 2:
                piper = new StreamPiper(in, runtime.getStderr(), false);
                piper.start(runtime.getUnboundedPool());
                break;
            default:
                throw new AssertionError("Only FDs 0, 1, and 2 supported");
            }

        default:
            throw new OSException(ErrorCodes.EINVAL, "Unsupported stdio type " + type);
        }
        return ret;
    }

    @SuppressWarnings("unused")
    public void spawn(ProcessInfo info, IntConsumer onExit)
        throws OSException
    {
        if (log.isDebugEnabled()) {
            log.debug("About to exec " + info.getArgs());
        }
        ProcessBuilder builder = new ProcessBuilder(info.getArgs());
        if (info.getCwd() != null) {
            File cwdFile = runtime.translatePath(info.getCwd());
            if (!cwdFile.exists()) {
                throw new OSException(ErrorCodes.ENOENT);
            } else if (!cwdFile.isDirectory()) {
                throw new OSException(ErrorCodes.ENOTDIR);
            }

            builder.directory(cwdFile);
        }

        /* TODO environment
        if (options.has("envPairs", options)) {
            setEnvironment(Utils.toStringList((Scriptable) options.get("envPairs", options)),
                           builder.environment());
        }
        */

        try {
            proc = builder.start();
        } catch (IOException ioe) {
            if (log.isDebugEnabled()) {
                log.debug("Error in execution: {}", ioe);
            }
            throw new OSException(ErrorCodes.ENOENT);
        }
        if (log.isDebugEnabled()) {
            log.debug("Starting {}", proc);
        }

        AbstractHandle h;
        h = createOutputStream(info.getStdioType(0), info.getStdioFd(0), proc.getOutputStream());
        if (h != null) {
            info.setStdioHandle(0, h);
        }
        h = createInputStream(info.getStdioType(1), info.getStdioFd(1), proc.getInputStream());
        if (h != null) {
            info.setStdioHandle(1, h);
        }
        h = createInputStream(info.getStdioType(2), info.getStdioFd(2), proc.getErrorStream());
        if (h != null) {
            info.setStdioHandle(2, h);
        }

        runtime.getUnboundedPool().submit(() -> {
            try {
                int exitCode = proc.waitFor();
                if (log.isDebugEnabled()) {
                    log.debug("Child process exited with {}", exitCode);
                }
                finished = true;
                runtime.enqueueTask(() -> onExit.accept(exitCode));
            } catch (InterruptedException ie) {
                finished = true;
                runtime.enqueueTask(() -> onExit.accept(0));
            }
        });
    }
}