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

import io.apigee.rowboat.NodeRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.function.BiConsumer;

public class DNSWrap
{
    private static final Logger log = LoggerFactory.getLogger(DNSWrap.class.getName());

    private final NodeRuntime runtime;

    @SuppressWarnings("unused")
    public DNSWrap(NodeRuntime runtime)
    {
        this.runtime = runtime;
    }

    /**
     * Lookup all the addresses that match a name and return as an array of strings. "family"
     * could be 4, 6, or 0 (for either).
     */
    @SuppressWarnings("unused")
    public void getAllByName(String name, int family, BiConsumer<Object, String[]> cb)
    {
        assert((family == 4) || (family == 6) || (family == 0));
        // Do the lookup in the "async" pool because it blocks but should not block forever
        runtime.getAsyncPool().execute(() -> {
            try {
                InetAddress[] result = InetAddress.getAllByName(name);
                ArrayList<String> rl = new ArrayList<>(result.length);
                for (InetAddress a : result) {
                    if ((family == 0) ||
                        ((family == 4) && (a instanceof Inet4Address)) ||
                        ((family == 6) && (a instanceof Inet6Address))) {
                        rl.add(a.getHostAddress());
                    }
                }

                String[] endResult = rl.toArray(new String[rl.size()]);
                if (log.isDebugEnabled()) {
                    log.debug("getAllByName({}, {}) = {}", name, family, endResult);
                }
                runtime.enqueueTask(() -> cb.accept(null, endResult));

            } catch (UnknownHostException uhe) {
                if (log.isDebugEnabled()) {
                    log.debug("getAllByName({}) = {}", name, uhe);
                }
                runtime.enqueueTask(() -> cb.accept(Constants.ENOTFOUND, null));
            }
        });
    }
}
