package io.apigee.rowboat.node010;

import io.apigee.trireme.kernel.Charsets;
import io.apigee.trireme.kernel.ErrorCodes;
import io.apigee.trireme.kernel.OSException;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.util.function.IntConsumer;

public class BufferUtils
{
    private static final BufferUtils myself = new BufferUtils();

    private BufferUtils()
    {
    }

    public static BufferUtils get() {
        return myself;
    }

    /**
     * Make a new buffer that starts at offset (at position 0) and has the given length.
     * It will share the underlying bytes.
     */
    @SuppressWarnings("unused")
    public ByteBuffer sliceBuffer(ByteBuffer bb, int offset, int length)
    {
        int oldPos = bb.position();
        bb.position(offset);

        ByteBuffer ret = bb.slice();
        bb.position(oldPos);
        ret.limit(length);
        return ret;
    }

    @SuppressWarnings("unused")
    public int getByteLength(String s, String encoding)
    {
        // Use CharSequence here to avoid an extra copy of the string that we don't need
        Charset cs = Charsets.get().resolveCharset(encoding);
        //assert(cs != null);
        if (cs == null) {
            throw new AssertionError("Encoding not found: " + encoding);
        }
        CharsetEncoder encoder = cs.newEncoder();
        encoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
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

    @SuppressWarnings("unused")
    public void fill(ByteBuffer buf, Object val, int start, int end)
        throws OSException
    {
        byte toFill;
        if (val instanceof Number) {
            toFill = (byte)((Number)val).intValue();
        } else if (val instanceof Boolean) {
            toFill = ((Boolean)val).booleanValue() ? (byte)1 : (byte)0;
        } else if (val instanceof CharSequence) {
            toFill = (byte)(((CharSequence)val).toString().charAt(0));
        } else {
            throw new OSException(ErrorCodes.EINVAL);
        }

        for (int i = start; i < end; i++) {
            buf.put(i, toFill);
        }
    }

    @SuppressWarnings("unused")
    public int copy(ByteBuffer src, ByteBuffer dst, int dstStart, int start, int end)
    {
        int len = Math.min(end - start, dst.limit() - dstStart);
        ByteBuffer srcTmp = src.duplicate();
        srcTmp.position(start);
        srcTmp.limit(start + len);

        ByteBuffer dstTmp = dst.duplicate();
        dstTmp.position(dstStart);
        dstTmp.limit(dstStart + len);
        dstTmp.put(srcTmp);

        return len;
    }

    @SuppressWarnings("unused")
    public String toString(ByteBuffer buf, int start, int end, Charset charset)
    {
        // Will need changing if we ever support direct buffers
        assert(buf.hasArray());
        return new String(buf.array(), buf.arrayOffset() + start, end - start, charset);
    }

    @SuppressWarnings("unused")
    public int write(ByteBuffer buf, CharSequence str, int offset, int length, Charset cs, IntConsumer updateWriteCount)
    {
        int len = Math.min(length, buf.limit() - offset);
        if (len <= 0) {
            updateWriteCount.accept(0);
            return 0;
        }

        ByteBuffer writeBuf = buf.duplicate();
        writeBuf.position(offset);
        writeBuf.limit(offset + len);

        CharBuffer chars = CharBuffer.wrap(str);
        CharsetEncoder encoder = cs.newEncoder();
        encoder.onUnmappableCharacter(CodingErrorAction.REPLACE);

        // Encode as much as we can and stop if we fail. Ignore overflow.
        encoder.encode(chars, writeBuf, true);
        encoder.flush(writeBuf);
        updateWriteCount.accept(chars.position());

        return writeBuf.position() - offset;
    }
}
