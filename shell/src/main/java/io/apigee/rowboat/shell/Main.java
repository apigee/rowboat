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
package io.apigee.rowboat.shell;

import io.apigee.rowboat.NodeEnvironment;
import io.apigee.rowboat.NodeException;
import io.apigee.rowboat.NodeScript;
import io.apigee.rowboat.ScriptStatus;
import jdk.nashorn.api.scripting.NashornException;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * This is the "main," which runs the script.
 */
public class Main
{
    private String scriptSource;
    private boolean runRepl;
    private boolean printEval;
    private String[] scriptArgs;

    private static void printUsage()
    {
        System.err.println("Usage: " + Main.class.getName() + " [options] [ -e script | script.js ] [arguments]");
        System.err.println();
        System.err.println("Options:");
        System.err.println("  -v, --version        Print Version");
        System.err.println("  -e, --eval script    Evaluate script");
        System.err.println("  -p, --print          Evaluate script and print result");
        System.err.println("  -i, --interactive    Enter the REPL even if stdin doesn't appear to be a terminal");
        // TODO --no-deprecation, --trace-deprecation
    }

    private static void printVersion()
    {
        NodeEnvironment env = new NodeEnvironment();
        System.err.println('v' + env.getDefaultNodeVersion());
    }

    public static void main(String[] args)
    {
        Main m = new Main();
        if (!m.parseArgs(args)) {
            System.exit(1);
        } else {
            int ec = m.run();
            System.exit(ec);
        }
    }

    private boolean parseArgs(String[] args)
    {
        int i = 0;
        boolean wasEval = false;
        while (i < args.length) {
            if ("-h".equals(args[i]) || "--help".equals(args[i])) {
                printUsage();
                return false;
            } else if ("-v".equals(args[i]) || "--version".equals(args[i])) {
                printVersion();
                return false;
            } else if (wasEval) {
                wasEval = false;
                scriptSource = args[i];
            } else if ("-i".equals(args[i]) || "--interactive".equals(args[i])) {
                runRepl = true;
            } else if ("-e".equals(args[i]) || "--eval".equals(args[i])) {
                wasEval = true;
            } else if ("-p".equals(args[i]) || "--print".equals(args[i])) {
                wasEval = true;
                printEval = true;
            } else {
                break;
            }
            i++;
        }

        if (i < args.length) {
            scriptArgs = new String[args.length - i];
            System.arraycopy(args, i, scriptArgs, 0, scriptArgs.length);
        } else {
            scriptArgs = new String[0];
        }
        return true;
    }

    private int run()
    {
        NodeEnvironment env = new NodeEnvironment();

        try {
            NodeScript ns;
            if (scriptSource != null) {
                // Force an "eval"
                String[] args = new String[scriptArgs.length + 2];
                args[0] = "-e";
                args[1] = scriptSource;
                System.arraycopy(scriptArgs, 0, args, 2, scriptArgs.length);
                ns = env.createScript(args);
                ns.setPrintEval(printEval);
            } else {
                ns = env.createScript(scriptArgs);
            }

            ScriptStatus status;
            try {
                Future<ScriptStatus> future = ns.execute();
                status = future.get();
            } finally {
                ns.close();
            }

            if (status.hasCause()) {
                printException(status.getCause());
            }

            return status.getExitCode();
        } catch (NodeException ne) {
            ne.printStackTrace(System.err);
            return 99;
        } catch (InterruptedException ie) {
            return 99;
        } catch (ExecutionException ee) {
            printException(ee.getCause());
            return 99;
        } finally {
            env.close();
        }
    }

    private static void printException(Throwable ee)
    {
        System.err.println(ee.getMessage());
        String scriptStack = NashornException.getScriptStackString(ee);
        if (scriptStack.isEmpty()) {
            ee.printStackTrace(System.err);
        } else {
            System.err.println(scriptStack);
        }
    }
}
