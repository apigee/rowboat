package io.apigee.rowboat.process;

import io.apigee.rowboat.NodeException;
import io.apigee.rowboat.NodeScript;
import io.apigee.rowboat.Sandbox;
import io.apigee.rowboat.ScriptFuture;
import io.apigee.rowboat.ScriptStatus;
import io.apigee.rowboat.internal.Constants;
import io.apigee.rowboat.internal.ScriptRunner;
import io.apigee.trireme.kernel.ErrorCodes;
import io.apigee.trireme.kernel.OSException;
import io.apigee.trireme.kernel.handles.AbstractHandle;
import io.apigee.trireme.kernel.handles.JavaInputStreamHandle;
import io.apigee.trireme.kernel.handles.JavaOutputStreamHandle;
import io.apigee.trireme.kernel.streams.BitBucketInputStream;
import io.apigee.trireme.kernel.streams.BitBucketOutputStream;
import io.apigee.trireme.kernel.streams.NoCloseInputStream;
import io.apigee.trireme.kernel.streams.NoCloseOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.function.IntConsumer;

public class SpawnedRowboatProcess
{
    // How much space to set aside for a pipe between processes
    public static final int PROCESS_PIPE_SIZE = 64 * 1024;

    private static final Logger log = LoggerFactory.getLogger(SpawnedOSProcess.class);

    private final ScriptRunner runtime;

    private ScriptFuture future;
    private NodeScript script;
    private boolean ipcEnabled;
    private boolean finished;

    @SuppressWarnings("unused")
    public SpawnedRowboatProcess(ScriptRunner runtime)
    {
        this.runtime = runtime;
    }

    @SuppressWarnings("unused")
    public void terminate(String code)
    {
        future.cancel(true);
    }

    @SuppressWarnings("unused")
    public void close()
    {
        if (!finished && (future != null)) {
            future.cancel(true);
        }
    }

    /**
     * Create a readable stream, and set "socket" to be a writable stream that writes to it.
     * Used for standard input.
     */
    private AbstractHandle createOutputStream(ProcessInfo.StdioType type, int fd, Sandbox sandbox)
        throws IOException, OSException
    {
        AbstractHandle handle = null;
        switch (type) {
        case PIPE:
            // Create a pipe between stdin of this new process and an output stream handle.
            // Use piped streams here for consistency so that we do everything using standard streams.
            if (log.isDebugEnabled()) {
                log.debug("Creating input stream pipe");
            }
            PipedInputStream pipeIn = new PipedInputStream(PROCESS_PIPE_SIZE);
            PipedOutputStream pipeOut = new PipedOutputStream(pipeIn);

            sandbox.setStdin(pipeIn);
            handle = new JavaOutputStreamHandle(pipeOut);
            break;

        case FD:
            // Child will read directly from the stdin for this process.
            if (fd != 0) {
                throw new OSException(ErrorCodes.EINVAL, "Invalid fd " + fd + " for process input");
            }
            log.debug("Using standard input for script input");
            sandbox.setStdin(new NoCloseInputStream(runtime.getStdin()));
            break;

        case IGNORE:
            // Just create a dummy stream in case someone needs to read from it
            sandbox.setStdin(new BitBucketInputStream());
            break;

        case IPC:
            ipcEnabled = true;
            break;

        default:
            throw new AssertionError();
        }
        return handle;
    }

    /**
     * Create a writable stream, and set "socket" to be a readable stream that reads from it.
     * Used for standard output and error.
     */
    private AbstractHandle createInputStream(ProcessInfo.StdioType type, int index, int fd, Sandbox sandbox)
        throws IOException
    {
        AbstractHandle handle = null;
        switch (type) {
        case PIPE:
            // Pipe between us using a pipe that has a maximum size and can block
            if (log.isDebugEnabled()) {
                log.debug("Creating writable stream pipe for stdio");
            }
            PipedInputStream pipeIn = new PipedInputStream(PROCESS_PIPE_SIZE);
            PipedOutputStream pipeOut = new PipedOutputStream(pipeIn);

            handle = new JavaInputStreamHandle(pipeIn, runtime);

            switch (index) {
            case 1:
                sandbox.setStdout(pipeOut);
                break;
            case 2:
                sandbox.setStderr(pipeOut);
                break;
            default:
                throw new AssertionError("Child process only supported on fds 1 and 2");
            }
            break;

        case FD:
            // Child will write directly to either stdout or stderr of this process.
            OutputStream out;
            switch (fd) {
            case 1:
                log.debug("Using standard output for script output");
                sandbox.setStdout(new NoCloseOutputStream(runtime.getStdout()));
                break;
            case 2:
                log.debug("Using standard error for script output");
                sandbox.setStdout(new NoCloseOutputStream(runtime.getStderr()));
                break;
            default:
                throw new AssertionError("Child process only supported on fds 1 and 2");
            }
            break;

        case IGNORE:
            // Just swallow all the output
            switch (index) {
            case 1:
                sandbox.setStdout(new BitBucketOutputStream());
                break;
            case 2:
                sandbox.setStderr(new BitBucketOutputStream());
                break;
            default:
                throw new AssertionError("Child process only supported on fds 1 and 2");
            }
            break;

        case IPC:
            ipcEnabled = true;
            break;

        default:
            throw new AssertionError();
        }
        return handle;
    }

    // If the name of the process is the same as "process.execPath" or if it is
    // "node" or "./node", then use the Environment to launch another script.
    // that means that we have to block for the process using a future rather than using the
    // thread that we have here. Use the environment to set stdin/out/err.
    // Also, this means that we will have to somehow modify Sandbox to add inheritance
    // and we will also have to modify Process to take differnet types of streams
    // for these streams.

    @SuppressWarnings("unused")
    public void spawn(ProcessInfo info, IntConsumer onExit)
        throws OSException
    {
        if (log.isDebugEnabled()) {
            log.debug("About to launch another ScriptRunner thread");
        }

        String scriptPath = null;
        int i;
        // Look at the args but slip the first one
        String[] execArgs = info.getArgs();
        for (i = 1; i < execArgs.length; i++) {
            if (!execArgs[i].startsWith("-")) {
                // Skip any parameters to "node" itself
                scriptPath = execArgs[i];
                i++;
                break;
            }
        }
        if (scriptPath == null) {
            throw new OSException(ErrorCodes.EINVAL, "No script path to spawn");
        }

        ArrayList<String> args = new ArrayList<String>(execArgs.length - i + 1);
        args.add(scriptPath);
        for (; i < execArgs.length; i++) {
            String arg = execArgs[i];
            if (arg != null) {
                args.add(arg);
            }
        }

        Sandbox scriptSandbox = new Sandbox(runtime.getSandbox());
        try {
            AbstractHandle h;
            h = createOutputStream(info.getStdioType(0), info.getStdioFd(0), scriptSandbox);
            if (h != null) {
                info.setStdioHandle(0, h);
            }
            h = createInputStream(info.getStdioType(1), 1, info.getStdioFd(1), scriptSandbox);
            if (h != null) {
                info.setStdioHandle(1, h);
            }
            h = createInputStream(info.getStdioType(2), 2, info.getStdioFd(2), scriptSandbox);
            if (h != null) {
                info.setStdioHandle(2, h);
            }
        } catch (IOException ioe) {
            throw new OSException(ErrorCodes.EIO, ioe);
        }

        /* TODO IPC!
        for (int si = 3; ; si++) {
            if (stdio.has(si, stdio)) {
                Scriptable stdioObj = getStdioObj(stdio, si);
                if (STDIO_IPC.equals(getStdioType(stdioObj))) {
                    ipcEnabled = true;
                } else {
                    throw Utils.makeError(cx, stdioObj, "Invalid stdio type " + getStdioType(stdioObj) +
                        " for stdio index " + si);
                }
            } else {
                break;
            }
        }
        */

        File cwdFile = null;
        if (info.getCwd() != null) {
            cwdFile = runtime.translatePath(info.getCwd());
            if (!cwdFile.exists()) {
                throw new OSException(ErrorCodes.ENOENT);
            } else if (!cwdFile.isDirectory()) {
                throw new OSException(ErrorCodes.ENOTDIR);
            }
        }

        /* TODO environment!
        HashMap<String, String> env = null;
        if (options.has("envPairs", options)) {
            env = new HashMap<String, String>();
            setEnvironment(Utils.toStringList((Scriptable) options.get("envPairs", options)),
                           env);
        }
        */

        try {
            script =
                runtime.getEnvironment().createScript(args.toArray(new String[args.size()]));
            script.setSandbox(scriptSandbox);
            if (cwdFile != null) {
                script.setWorkingDirectory(cwdFile.getPath());
            }
            /* TODO
            if (env != null) {
                script.setEnvironment(env);
            }

            if (ipcEnabled) {
                script._setParentProcess(parent);
            }
            */

            future = script.execute();
        } catch (NodeException ne) {
            if (log.isDebugEnabled()) {
                log.debug("Error starting internal script: {}", ne);
            }
            throw new OSException(ErrorCodes.EIO, ne);
        }

        future.setListener((NodeScript script, ScriptStatus status) ->
            {
                if (log.isDebugEnabled()) {
                    log.debug("Child ScriptRunner exited: {}", status);
                }
                finished = true;
                script.close();
                runtime.enqueueTask(() -> onExit.accept(status.getExitCode()));
            });
    }

    /* TODO
    @Override
    protected Scriptable getChildProcessObject() {
        return script._getProcessObject();
    }

    public void send(Context cx, Object message)
    {
        script._getRuntime().enqueueIpc(cx, message, null);
    }
    */
}