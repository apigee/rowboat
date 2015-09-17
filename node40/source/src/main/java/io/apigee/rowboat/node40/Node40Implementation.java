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

package io.apigee.rowboat.node40;

import io.apigee.rowboat.NodeModule;
import io.apigee.rowboat.spi.NodeImplementation;

import java.util.Collection;
import java.util.Collections;

public class Node40Implementation
    implements NodeImplementation
{
    private static final String B = "/node40";
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
            { "_debug_agent", N + "_debug_agent.js" },
            { "_debugger", N + "_debugger.js" },
            { "_http_agent", N + "_http_agent.js" },
            { "_http_client", N + "_http_client.js" },
            { "_http_common", N + "_http_common.js" },
            { "_http_incoming", N + "_http_incoming.js" },
            { "_http_outgoing", N + "_http_outgoing.js" },
            { "_http_server", N + "_http_server.js" },
            { "_linklist", N + "_linklist.js" },
            { "_stream_duplex", N + "_stream_duplex.js" },
            { "_stream_passthrough", N + "_stream_passthrough.js" },
            { "_stream_readable", N + "_stream_readable.js" },
            { "_stream_transform", N + "_stream_transform.js" },
            { "_stream_wrap", N + "_stream_wrap.js" },
            { "_stream_writable", N + "_stream_writable.js" },
            { "_tls_common", N + "_tls_common.js" },
            { "_tls_legacy", N + "_tls_legacy.js" },
            { "_tls_wrap", N + "_tls_wrap.js" },
            { "assert", N + "assert.js" },
            { "child_process", N + "child_process.js" },
            { "cluster", N + "cluster.js" },
            { "console", N + "console.js" },
            { "constants", N + "constants.js" },
            { "crypto", N + "crypto.js" },
            { "dgram", N + "dgram.js" },
            { "dns", N + "dns.js" },
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
            { "smalloc", N + "smalloc.js" },
            { "stream", N + "stream.js" },
            { "string_decoder", N + "string_decoder.js" },
            { "sys", N + "sys.js" },
            { "timers", N + "timers.js" },
            { "tls", N + "tls.js" },
            { "url", N + "url.js" },
            { "util", N + "util.js" },
            { "v8", N + "v8.js" },
            { "vm", N + "vm.js" },
            { "zlib", N + "zlib.js" },

            // Rowboat
            { "buffer", R + "buffer.js" },
            { "child_process", R + "child_process.js" },
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
            { "http_parser", R + "http_parser.js" },
            { "process", R + "process.js" },
            { "process_wrap", R + "process_wrap.js" },
            { "referenceable", R + "referenceable.js" },
            { "buffer", R + "slowbuffer.js" },
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
        return Collections.emptyList();
    }
}
