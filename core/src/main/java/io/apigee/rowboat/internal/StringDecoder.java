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
package io.apigee.rowboat.internal;

import io.apigee.trireme.kernel.Charsets;
import io.apigee.trireme.kernel.util.BufferUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;

public class StringDecoder
{
    private static final Logger log = LoggerFactory.getLogger(StringDecoder.class.getName());
    private static final ByteBuffer EMPTY = ByteBuffer.allocate(0);

    private ByteBuffer remaining;
    private final CharsetDecoder decoder;

    public StringDecoder(String encoding)
    {
        Charset cs = Charsets.get().resolveCharset(encoding);
        assert(cs != null);
        decoder = cs.newDecoder();
    }

    @SuppressWarnings("unused")
    public String decode(ByteBuffer buf)
    {
        return doDecode(buf, false);
    }

    @SuppressWarnings("unused")
    public String end(ByteBuffer buf)
    {
        return doDecode(buf, true);
    }

    @SuppressWarnings("unused")
    public static boolean isEncoding(String encoding)
    {
        return (Charsets.get().getCharset(encoding) != null);
    }

    private String doDecode(ByteBuffer buf, boolean lastChunk)
    {
        ByteBuffer inBuf = (buf == null ? EMPTY : buf);
        ByteBuffer allIn = BufferUtils.catBuffers(remaining, inBuf);
        CharBuffer out =
            CharBuffer.allocate((int) Math.ceil(inBuf.remaining() * decoder.averageCharsPerByte()));

        if (log.isTraceEnabled()) {
            log.trace("Decoding {} bytes", allIn.remaining());
        }

        CoderResult result;
        do {
            result = decoder.decode(allIn, out, lastChunk);
            if (result.isOverflow()) {
                out = BufferUtils.doubleBuffer(out);
            }
        } while (result.isOverflow());
        if (lastChunk) {
            do {
                result = decoder.flush(out);
                if (result.isOverflow()) {
                    out = BufferUtils.doubleBuffer(out);
                }
            } while (result.isOverflow());
        }

        if (allIn.hasRemaining()) {
            if (log.isTraceEnabled()) {
                log.trace("Decoding leaves {} bytes left over", allIn.remaining());
            }
            remaining = allIn;
        } else {
            remaining = null;
        }

        if (out.position() > 0) {
            out.flip();
            if (log.isTraceEnabled()) {
                log.trace("Returning {}", out.toString());
            }
            return out.toString();
        }
        if (log.isTraceEnabled()) {
            log.trace("Returning nothing");
        }
        return "";
    }
}
