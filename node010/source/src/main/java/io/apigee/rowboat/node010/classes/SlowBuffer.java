package io.apigee.rowboat.node010.classes;

import io.apigee.rowboat.binding.AbstractScriptObject;
import io.apigee.rowboat.binding.JSConstructor;
import io.apigee.rowboat.internal.Buffer;
import io.apigee.rowboat.internal.Charsets;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Because of some prototype hacking, all instances of the "SlowBuffer" class in the product
 * will actually be an instance of this class with additional functions added. This class's
 * job is to hold the array of bytes. Because we need to call some Java code to make things
 * go, the actual holding and manipulating of the bytes is done in the "Buf" class.
 */

public class SlowBuffer
    extends AbstractScriptObject
    implements Buffer
{
    public static final String CLASS_NAME = "SlowBuffer";

    private Consumer<SlowBuffer> constructor;
    private BiConsumer<SlowBuffer, Buf> internalConstructor;

    private static final Buf EMPTY_BUF = new Buf(0);

    private final Buf buf;

    @Override
    public String getClassName() {
        return CLASS_NAME;
    }

    /**
     * buffer.js calls this to give us a function to call to wire up the prototype for a buffer.
     */
    @SuppressWarnings("unused")
    public static void setConstructor(SlowBuffer target, Consumer<SlowBuffer> func)
    {
        target.constructor = func;
    }

    /**
     * slowbuffer.js calls this to give us a function to call to wire up the prototype.
     */
    @SuppressWarnings("unused")
    public static void setInternalConstructor(SlowBuffer target, BiConsumer<SlowBuffer, Buf> func)
    {
        target.internalConstructor = func;
    }

    /**
     * buffer.js call this from the "toJava" method. It turns the guts of the buffer into a
     * ByteBuffer that can be consumed from Java code.
     */
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
        buf = EMPTY_BUF;
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
        ret.setMember("length", length);
        if (constructor != null) {
            constructor.accept(ret);
        }
        if (internalConstructor != null) {
            // slowbuffer.js has some functions that override those in buffer.js, so
            // set those up here. This function must be called second.
            internalConstructor.accept(ret, ret.buf);
        }
        return ret;
    }

    @Override
    public Object call(Object thiz, Object... args)
    {
        return newObject(args);
    }

    // These functions make JavaScript objects based on this object behave like an array of ints.

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

    /**
     * Calculate the length of the string based on encoding. Sadly the only way to do this is to actually
     * encode the string.
     */
    @SuppressWarnings("unused")
    public static int calculateByteLength(CharSequence s, String encoding)
    {
        // Use CharSequence here to avoid an extra copy of the string that we don't need
        Charset cs = Charsets.get().resolveCharset(encoding);
        assert(cs != null);
        CharsetEncoder encoder = cs.newEncoder();
        if (encoder.maxBytesPerChar() == 1.0f) {
            // Optimize for ascii
            return s.length();
        }

        // Encode the string to a temporary buffer so that we can count
        CharBuffer chars = CharBuffer.wrap(s);
        ByteBuffer tmp = ByteBuffer.allocate(256);
        int total = 0;
        CoderResult result;
        do {
            tmp.clear();
            result = encoder.encode(chars, tmp, true);
            total += tmp.position();
        } while (result.isOverflow());
        do {
            tmp.clear();
            result = encoder.flush(tmp);
            total += tmp.position();
        } while (result.isOverflow());
        return total;
    }

    /**
     * Put the actual bytes into a separate object that can be called as if it were Java.
     * Having a single object that can be called from JS and from Java is very confusing to Nashorn.
     * So, objects on the "slowbuffer.js" class call this directly.
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

        /**
         * Write as much of the string as will fit to the buffer at the appropriate place.
         */
        @SuppressWarnings("unused")
        public int write(CharSequence str, int offset, int length, Charset cs, IntConsumer updateWriteCount)
        {
            ByteBuffer writeBuf = ByteBuffer.wrap(buffer, bufOffset + offset, length);
            CharBuffer chars = CharBuffer.wrap(str);
            CharsetEncoder encoder = cs.newEncoder();

            // Encode as much as we can and stop if we fail. Ignore overflow.
            // "getBytes" would be more efficient here for ascii and UTF-8 but we'd have to copy the result
            encoder.encode(chars, writeBuf, true);
            encoder.flush(writeBuf);
            updateWriteCount.accept(chars.position());

            return writeBuf.position() - offset - bufOffset;
        }

        /**
         * Turn the contents of the buffer into a string. "new String" is the most efficient way to
         * do this in almost all cases, especially ascii and UTF-8.
         */
        @SuppressWarnings("unused")
        public String slice(int start, int end, Charset cs)
        {
            return new String(buffer, bufOffset + start, end - start, cs);
        }
    }
}

