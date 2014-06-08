package io.apigee.rowboat.node010.classes;

import io.apigee.rowboat.binding.AbstractScriptObject;
import io.apigee.rowboat.binding.JSConstructor;
import io.apigee.rowboat.internal.Buffer;
import io.apigee.rowboat.internal.Charsets;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Because of some prototype hacking, all instances of the "SlowBuffer" class in the product
 * will actually be an instance of this class with additional functions added. This class's
 * job is to hold the array of bytes.
 */

public class SlowBuffer
    extends AbstractScriptObject
    implements Buffer
{
    public static final String CLASS_NAME = "SlowBuffer";

    private Consumer<SlowBuffer> constructor;
    private BiConsumer<SlowBuffer, Buf> internalConstructor;

    private Buf buf;

    @Override
    public String getClassName() {
        return CLASS_NAME;
    }

    @SuppressWarnings("unused")
    public static void setConstructor(SlowBuffer target, Consumer<SlowBuffer> func)
    {
        target.constructor = func;
    }

    @SuppressWarnings("unused")
    public static void setInternalConstructor(SlowBuffer target, BiConsumer<SlowBuffer, Buf> func)
    {
        target.internalConstructor = func;
    }

    @SuppressWarnings("unused")
    public static ByteBuffer convertBuffer(SlowBuffer target, int offset, int length)
    {
        ByteBuffer bb = target.getBuffer();
        bb.position(bb.position() + offset);
        bb.limit(bb.position() + length);
        return bb;
    }

    public SlowBuffer()
    {
    }

    public SlowBuffer(int length)
    {
        buf = new Buf(length);
    }

    @Override
    public ByteBuffer getBuffer()
    {
        return buf.getBuffer();
    }

    @Override
    public Object newObject(Object... args)
    {
        int length = ((Number)args[0]).intValue();
        SlowBuffer ret = new SlowBuffer(length);
        if (constructor != null) {
            constructor.accept(ret);
        }
        if (internalConstructor != null) {
            // slowbuffer.js has some functions that override those in buffer.js, so
            // set those up here.
            internalConstructor.accept(ret, ret.buf);
        }
        return ret;
    }

    @Override
    public Object getSlot(int index)
    {
        return buf.getSlot(index);
    }

    @Override
    public void setSlot(int i, Object v)
    {
        buf.setSlot(i, v);
    }

    @Override
    public boolean hasSlot(int i)
    {
        return buf.hasSlot(i);
    }

    @Override
    public boolean isInstance(Object o)
    {
        return (o instanceof SlowBuffer);
    }

    @SuppressWarnings("unused")
    public static int calculateByteLength(CharSequence s, String encoding)
    {
        // Use CharSequence here to avoid an extra copy of the string that we don't need
        if ("ascii".equals(encoding)) {
            return s.length();
        }
        Charset cs = Charsets.get().resolveCharset(encoding);
        assert(cs != null);
        // TODO we could hyper-optimize this but at the same time it is faster to use "getBytes"
        return (s.toString().getBytes(cs).length);
    }

    /**
     * Put the actual bytes into a separate object that can be called as if it were Java.
     * Having a single object that can be called from JS and from Java is very confusing to Nashorn.
     */

    public static class Buf
    {
        private byte[] buffer;
        private int bufOffset;

        Buf(int length)
        {
            this.buffer = new byte[length];
        }

        ByteBuffer getBuffer()
        {
            return ByteBuffer.wrap(buffer);
        }

        Object getSlot(int index)
        {
            if ((index >= 0) && (index < buffer.length)) {
                return buffer[index] & 0xff;
            }
            return null;
        }

        void setSlot(int i, Object v)
        {
            if ((i >= 0) && (i < buffer.length)) {
                int val = ((Number)v).intValue();
                if (val < 0) {
                    val = 0xff + val + 1;
                }
                buffer[i] = (byte)(val & 0xff);
            }
        }
        boolean hasSlot(int i)
        {
            return ((i >= 0) && (i < buffer.length));
        }

        @SuppressWarnings("unused")
        public int write(CharSequence str, int offset, int length, Charset cs, IntConsumer updateWriteCount)
        {
            ByteBuffer writeBuf = ByteBuffer.wrap(buffer, bufOffset + offset, length);
            CharBuffer chars = CharBuffer.wrap(str);
            CharsetEncoder encoder = cs.newEncoder();

            // Encode as much as we can and stop if we fail.
            encoder.encode(chars, writeBuf, true);
            encoder.flush(writeBuf);
            updateWriteCount.accept(chars.position());

            return writeBuf.position() - offset - bufOffset;
        }

        @SuppressWarnings("unused")
        public String slice(int offset, int length, Charset cs)
        {
            return new String(buffer, bufOffset + offset, length, cs);
        }
    }
}

