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

package io.apigee.rowboat.node010;

import io.apigee.rowboat.NodeModule;
import io.apigee.rowboat.node010.modules.InternalBufferModule;
import io.apigee.rowboat.spi.NodeImplementation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public class Node010Implementation
    implements NodeImplementation
{
    private static final String B = "/node010";
    private static final String R = B + "/rowboat/";
    private static final String N = B + "/node/";

    @Override
    public String getVersion()
    {
        return "0.10.25";
    }

    @Override
    public String getMainScript()
    {
        return R + "trireme.js";
    }

    @Override
    public String[][] getBuiltInModules()
    {
        return new String[][] {
            // From node
            { "_debugger", N + "_debugger.js" },
            { "_linklist", N + "_linklist.js" },
            { "_stream_duplex", N + "_stream_duplex.js" },
            { "_stream_passthrough", N + "_stream_passthrough.js" },
            { "_stream_readable", N + "_stream_readable.js" },
            { "_stream_transform", N + "_stream_transform.js" },
            { "_stream_writable", N + "_stream_writable.js" },
            { "assert", N + "assert.js" },
            { "cluster", N + "cluster.js" },
            { "console", N + "console.js" },
            { "constants", N + "constants.js" },
            { "crypto", N + "crypto.js" },
            { "dgram", N + "dgram.js" },
            { "domain", N + "domain.js" },
            { "events", N + "events.js" },
            { "freelist", N + "freelist.js" },
            { "fs", N + "fs.js" },
            { "http", N + "http.js" },
            { "https", N + "https.js" },
            { "module", N + "module.js" },
            { "net", N + "net.js" },
            { "os", N + "os.js" },
            { "path", N + "path.js" },
            { "punycode", N + "punycode.js" },
            { "querystring", N + "querystring.js" },
            { "readline", N + "readline.js" },
            { "stream", N + "stream.js" },
            { "sys", N + "sys.js" },
            { "timers", N + "timers.js" },
            { "url", N + "url.js" },
            { "util", N + "util.js" },

            // Rowboat
            { "buffer", R + "buffer.js" },
            { "string_decoder", R + "string_decoder.js" },
            { "vm", R + "vm.js" }
        };
    }

    @Override
    public String[][] getInternalModules()
    {
        return new String[][] {
            { "cares_wrap", R + "cares_wrap.js" },
            { "constants", R + "constants.js" },
            { "console_wrap", R + "console_wrap.js" },
            { "evals", R + "evals.js" },
            { "fs", R + "fs.js" },
            { "process", R + "process.js" },
            { "referenceable", R + "referenceable.js" },
            { "slowbuffer", R + "slowbuffer.js" },
            { "stream_wrap", R + "stream_wrap.js" },
            { "tcp_wrap", R + "tcp_wrap.js" },
            { "timer_wrap", R + "timer_wrap.js" }
        };
    }

    @Override
    public Collection<Class<? extends NodeModule>> getJavaModules()
    {
        return Collections.emptyList();
    }

    @Override
    public Collection<Class<? extends NodeModule>> getInternalJavaModules()
    {
        ArrayList<Class<? extends NodeModule>> cl = new ArrayList<>();
        cl.add(InternalBufferModule.class);
        return cl;
    }
}
