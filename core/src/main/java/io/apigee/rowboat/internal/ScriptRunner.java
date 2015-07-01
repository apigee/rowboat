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

import io.apigee.rowboat.NodeEnvironment;
import io.apigee.rowboat.NodeException;
import io.apigee.rowboat.NodeModule;
import io.apigee.rowboat.NodeRuntime;
import io.apigee.rowboat.NodeScript;
import io.apigee.rowboat.Sandbox;
import io.apigee.rowboat.ScriptFuture;
import io.apigee.rowboat.ScriptStatus;
import io.apigee.rowboat.ScriptTask;
import io.apigee.rowboat.binding.DefaultScriptObject;
import io.apigee.trireme.kernel.PathTranslator;
import io.apigee.trireme.kernel.net.NetworkPolicy;
import io.apigee.trireme.kernel.net.SelectorHandler;
import jdk.nashorn.api.scripting.JSObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * This class actually runs the script.
 */
public class ScriptRunner
    implements NodeRuntime, Callable<ScriptStatus>
{
    private static final Logger log = LoggerFactory.getLogger(ScriptRunner.class);

    private static final long DEFAULT_DELAY = Integer.MAX_VALUE;
    /** We don't really know what the umask is in Java, so we set a reasonable default that the tests expected. */
    public static final int DEFAULT_UMASK = 022;
    public static final String EXECUTABLE_NAME = "node";

    public static final String TIMEOUT_TIMESTAMP_KEY = "_tickTimeout";

    private static final ThreadLocal<ScriptRunner> threadRunner = new ThreadLocal<>();
    private static final ScriptEngineManager engineManager = new ScriptEngineManager();

    private final  NodeEnvironment env;
    private final  ModuleRegistry  registry;
    private final  NodeScript      scriptObject;
    private final  String[]        args;
    private        ScriptFuture    future;
    private final  CountDownLatch  initialized = new CountDownLatch(1);
    private final  Sandbox         sandbox;
    private final  PathTranslator  pathTranslator;
    private final  ExecutorService asyncPool;
    private final IdentityHashMap<Closeable, Closeable> openHandles =
        new IdentityHashMap<>();

    private final  ConcurrentLinkedQueue<Activity> tickFunctions = new ConcurrentLinkedQueue<>();
    private final  PriorityQueue<Activity>         timerQueue    = new PriorityQueue<>();
    private final  Selector                        selector;
    private        int                             timerSequence;
    private final  AtomicInteger                   pinCount      = new AtomicInteger(0);
    private int    exitCode = -1;

    // Globals that are set up for the process
    protected JSObject          process;
    protected JSObject          submitTick;
    private   Function<Object, Boolean> handleFatal;
    private   Runnable          tickFromSpinner;
    private   Runnable          immediateCallback;
    private Function<Throwable, Object> errorConverter;

    //private Buffer.BufferModuleImpl buffer;
    private String              workingDirectory;
    private Object              parentProcess;
    private Object              domain;

    private boolean             needTickCallback;
    private boolean             needImmediateCallback;
    private int                 umask = DEFAULT_UMASK;
    private ScriptContext       context;
    private ScriptEngine        engine;
    private Invocable           invocableEngine;

    public ScriptRunner(NodeScript so, NodeEnvironment env, Sandbox sandbox,
                        ModuleRegistry registry, String[] args)
    {
        this.env = env;
        this.scriptObject = so;
        this.registry = registry;
        this.args = args;
        this.sandbox = sandbox;
        this.pathTranslator = new PathTranslator();

        if ((sandbox != null) && (sandbox.getFilesystemRoot() != null)) {
            try {
                pathTranslator.setRoot(sandbox.getFilesystemRoot());
            } catch (IOException ioe) {
                throw new AssertionError("Unexpected I/O error setting filesystem root: " + ioe);
            }
        }

        if ((sandbox != null) && (sandbox.getWorkingDirectory() != null)) {
            this.workingDirectory = sandbox.getWorkingDirectory();
        } else if ((sandbox != null) && (sandbox.getFilesystemRoot() != null)) {
            this.workingDirectory = "/";
        } else {
            this.workingDirectory = new File(".").getAbsolutePath();
        }
        pathTranslator.setWorkingDir(workingDirectory);

        if ((sandbox != null) && (sandbox.getAsyncThreadPool() != null)) {
            this.asyncPool = sandbox.getAsyncThreadPool();
        } else {
            this.asyncPool = env.getAsyncPool();
        }

        if ((sandbox != null) && (sandbox.getMounts() != null)) {
            for (Map.Entry<String, String> mount : sandbox.getMounts()) {
                pathTranslator.mount(mount.getKey(), new File(mount.getValue()));
            }
        }

        try {
            this.selector = Selector.open();
        } catch (IOException ioe) {
            throw new AssertionError(ioe);
        }
    }

    public String getNodeVersion() {
        return registry.getImplementation().getVersion();
    }

    public void close()
    {
        try {
            selector.close();
        } catch (IOException ioe) {
            log.debug("Error closing selector", ioe);
        }
    }

    public void setFuture(ScriptFuture future) {
        this.future = future;
    }

    public ScriptFuture getFuture() {
        return future;
    }

    public NodeEnvironment getEnvironment() {
        return env;
    }

    public ModuleRegistry getRegistry() {
        return registry;
    }

    @Override
    public Sandbox getSandbox() {
        return sandbox;
    }

    @Override
    public NetworkPolicy getNetworkPolicy() {
        return (sandbox == null ? null : sandbox.getNetworkPolicy());
    }

    @Override
    public NodeScript getScriptObject() {
        return scriptObject;
    }

    public ScriptEngine getScriptEngine() {
        return engine;
    }

    public ScriptContext getScriptContext() {
        return context;
    }

    /**
     * Indicate that we will exit normally on the next exception caught.
     */
    public void setExitCode(int code) {
       this.exitCode = code;
    }

    public int getExitCode() {
        return exitCode;
    }

    @SuppressWarnings("unused")
    public String getWorkingDirectory() {
        return workingDirectory;
    }

    @SuppressWarnings("unused")
    public void setWorkingDirectory(String wd)
        throws IOException
    {
        File wdf = new File(wd);
        if (wdf.isAbsolute()) {
            this.workingDirectory = wd;
        } else {
            File newWdf = new File(this.workingDirectory, wd);
            this.workingDirectory = newWdf.getCanonicalPath();
        }
        pathTranslator.setWorkingDir(this.workingDirectory);
    }

    @Override
    public Selector getSelector() {
        return selector;
    }

    @Override
    public ExecutorService getAsyncPool() {
        return asyncPool;
    }

    @Override
    public ExecutorService getUnboundedPool() {
        return env.getScriptPool();
    }

    public InputStream getStdin() {
        return ((sandbox != null) && (sandbox.getStdin() != null)) ? sandbox.getStdin() : System.in;
    }

    public OutputStream getStdout() {
        return ((sandbox != null) && (sandbox.getStdout() != null)) ? sandbox.getStdout() : System.out;
    }

    public OutputStream getStderr() {
        return ((sandbox != null) && (sandbox.getStderr() != null)) ? sandbox.getStderr() : System.err;
    }

    public Object getParentProcess() {
        return parentProcess;
    }

    public JSObject getProcess() {
        return process;
    }

    @SuppressWarnings("unused")
    public void setSubmitTick(JSObject submit) {
        this.submitTick = submit;
    }

    public JSObject getSubmitTick() {
        return submitTick;
    }

    @SuppressWarnings("unused")
    public void setHandleFatal(Function<Object, Boolean> h) {
        this.handleFatal = h;
    }

    @SuppressWarnings("unused")
    public Function<Object, Boolean> getHandleFatal() {
        return handleFatal;
    }

    @SuppressWarnings("unused")
    public void setTickFromSpinner(Runnable t) {
        this.tickFromSpinner = t;
    }

    public Runnable getTickFromSpinner() {
        return tickFromSpinner;
    }

    public void setParentProcess(Object parentProcess) {
        this.parentProcess = parentProcess;
    }

    @SuppressWarnings("unused")
    public void setNeedTickCallback(boolean need) {
        this.needTickCallback = need;
    }

    @SuppressWarnings("unused")
    public boolean isNeedTickCallback() {
        return needTickCallback;
    }

    @SuppressWarnings("unused")
    public void setNeedImmediateCallback(boolean need) {
        this.needImmediateCallback = need;
    }

    @SuppressWarnings("unused")
    public boolean isNeedImmediateCallback() {
        return needImmediateCallback;
    }

    @SuppressWarnings("unused")
    public void setImmediateCallback(Runnable c) {
        this.immediateCallback = c;
    }

    @SuppressWarnings("unused")
    public Runnable getImmediateCallback() {
        return immediateCallback;
    }

    @SuppressWarnings("unused")
    public void setUmask(int umask) {
        this.umask = umask;
    }

    @SuppressWarnings("unused")
    public int getUmask() {
        return umask;
    }

    @SuppressWarnings("unused")
    public void setErrorConverter(Function<Throwable, Object> ec) {
        this.errorConverter = ec;
    }

    public Object convertError(Throwable t)
    {
        return errorConverter.apply(t);
    }

    /**
     * We use this when spawning child scripts to avoid sending them messages before they are ready.
     */
    public void awaitInitialization()
    {
        try {
            initialized.await();
        } catch (InterruptedException ignore) {
        }
    }

    /**
     * Translate a path based on the root.
     */
    @Override
    public File translatePath(String path)
    {
        File pf = new File(path);
        return pathTranslator.translate(pf.getPath());
    }

    @Override
    public String reverseTranslatePath(String path)
        throws IOException
    {
        return pathTranslator.reverseTranslate(path);
    }

    public PathTranslator getPathTranslator()
    {
        return pathTranslator;
    }

    /**
     * This method uses a concurrent queue so it may be called from any thread.
     */
    @Override
    public void enqueueCallback(JSObject f, Object thisObj, Object[] args)
    {
       enqueueCallback(f, thisObj, null, args);
    }

    /**
     * This method uses a concurrent queue so it may be called from any thread.
     */
    @Override
    public void enqueueCallback(JSObject f, Object thisObj, Object domain, Object[] args)
    {
        Callback cb = new Callback(f, thisObj, args);
        cb.setDomain(domain);
        tickFunctions.offer(cb);
        selector.wakeup();
    }

    /**
     * This method uses a concurrent queue so it may be called from any thread.
     */
    @Override
    public void enqueueTask(ScriptTask task)
    {
        Task t = new Task(task);
        tickFunctions.offer(t);
        selector.wakeup();
    }

    /**
     * This method uses a concurrent queue so it may be called from any thread.
     */
    public void enqueueTask(ScriptTask task, Object domain)
    {
        Task t = new Task(task);
        t.setDomain(domain);
        tickFunctions.offer(t);
        selector.wakeup();
    }

    @Override
    public void executeScriptTask(Runnable task, Object domain)
    {
        enqueueTask(() -> task.run(), domain);
    }

    /**
     * This method puts the task directly on the timer queue, which is unsynchronized. If it is ever used
     * outside the context of the "TimerWrap" module then we need to check for synchronization, add an
     * assertion check, or synchronize the timer queue.
     */
    public Activity createTimer(long delay, boolean repeating, long repeatInterval,
                                Consumer<Object> task, Object target)
    {
        Task t = new Task(() -> task.accept(target));
        long timeout = System.currentTimeMillis() + delay;
        int seq = timerSequence++;

        if (log.isDebugEnabled()) {
            log.debug("Going to fire timeout {} at {}", seq, timeout);
        }
        t.setId(seq);
        t.setTimeout(timeout);
        if (repeating) {
            t.setInterval(repeatInterval);
            t.setRepeating(true);
        }
        timerQueue.add(t);
        selector.wakeup();
        return t;
    }

    public Future<Boolean> createTimedTask(Runnable r, long delay, TimeUnit unit, boolean repeating, Object domain)
    {
        final RunnableTask t = new RunnableTask(r);
        t.setDomain(domain);
        t.setTimeout(System.currentTimeMillis() + unit.toMillis(delay));
        t.setRepeating(repeating);
        if (repeating) {
            t.setInterval(delay);
        }

        enqueueTask(new ScriptTask()
        {
            @Override
            public void execute()
            {
                if (!t.isCancelled()) {
                    t.setId(timerSequence++);
                    timerQueue.add(t);
                    selector.wakeup();
                }
            }
        });
        return t;
    }

    @Override
    public void pin()
    {
        int currentPinCount = pinCount.incrementAndGet();
        log.debug("Pin count is now {}", currentPinCount);
    }

    @Override
    public void unPin()
    {
        int currentPinCount = pinCount.decrementAndGet();
        log.debug("Pin count is now {}", currentPinCount);

        if (currentPinCount < 0) {
            log.warn("Negative pin count: {}", currentPinCount);
        }
        if (currentPinCount == 0) {
            selector.wakeup();
        }
    }

    public void setErrno(String err)
    {
        context.setAttribute("errno", err, ScriptContext.ENGINE_SCOPE);
    }

    public void clearErrno()
    {
        context.setAttribute("errno", 0, ScriptContext.ENGINE_SCOPE);
    }

    public Object getErrno()
    {
        return context.getAttribute("errno");
    }

    @Override
    public Object getDomain()
    {
        return domain;
    }

    public void setDomain(Object domain)
    {
        this.domain = domain;
    }

    @Override
    public void registerCloseable(Closeable c)
    {
        openHandles.put(c, c);
    }

    @Override
    public void unregisterCloseable(Closeable c)
    {
        openHandles.remove(c);
    }

    /**
     * Clean up all the leaked handles and file descriptors.
     */
    private void closeCloseables()
    {
        //AbstractFilesystem fs = (AbstractFilesystem)requireInternal("fs", cx);
        //fs.cleanup();

        for (Closeable c: openHandles.values()) {
            if (log.isDebugEnabled()) {
                log.debug("Closing leaked handle {}", c);
            }
            try {
                c.close();
            } catch (IOException ioe) {
                if (log.isDebugEnabled()) {
                    log.debug("Error closing leaked handle: {}", ioe);
                }
            }
        }
    }

    /**
     * Execute the script.
     */
    @Override
    public ScriptStatus call()
        throws NodeException
    {
        threadRunner.set(this);
        ScriptStatus status;

        try {
            engine = engineManager.getEngineByName("nashorn");
            assert(engine instanceof Compilable);
            assert(engine instanceof Invocable);
            invocableEngine = (Invocable)engine;

            context = engine.getContext();

            // Lazy first-time init of the node version.
            registry.load();

            try {
                initGlobals();
            } catch (NodeException ne) {
                return new ScriptStatus(ne);
            } finally {
                initialized.countDown();
            }

            // Run "trireme.js," which is our equivalent of "node.js". It returns a function that takes
            // "process". When done, we may have ticks to execute.
            String mainScript = registry.getMainScriptSource();
            CompiledScript mainCompiled =
                ((Compilable)engine).compile(mainScript);
            mainCompiled.eval();
            //engine.eval(mainScript);

            boolean timing = startTiming();
            try {
                // Pass the bindings (the globals) to the main function because it has to set its
                // "global" variables in there.
                invocableEngine.invokeFunction("_triremeMain", process,
                                               engine.getBindings(ScriptContext.ENGINE_SCOPE));
            } catch (Throwable t) {
                boolean handled = handleScriptException(t);
                if (!handled) {
                    throw t;
                }
            } finally {
                if (timing) {
                    endTiming();
                }
            }

            status = mainLoop();

        } catch (NodeExitException ne) {
            // This exception is thrown by process.exit()
            status = ne.getStatus();
        } catch (IOException ioe) {
            log.debug("I/O exception processing script: {}", ioe);
            status = new ScriptStatus(ioe);
        } catch (Throwable t) {
            if (exitCode >= 0) {
                return new ScriptStatus(exitCode);
            }
            log.debug("Unexpected script error: {}", t);
            status = new ScriptStatus(t);
        }

        log.debug("Script exiting with exit code {}", status.getExitCode());

        if (!status.hasCause() && !Boolean.TRUE.equals(process.getMember("_exiting"))) {
            // Fire the exit callback, but only if we aren't exiting due to an unhandled exception, and "exit"
            // wasn't already fired because we called "exit"
            try {
                process.setMember("_exiting", true);
                JSObject emit = (JSObject)process.getMember("emit");
                assert(emit != null);
                assert(emit.isFunction());
                emit.call(process, "exit", status.getExitCode());

            } catch (NodeExitException ee) {
                // Exit called exit -- allow it to replace the exit code
                log.debug("Script replacing exit code with {}", ee.getCode());
                status = ee.getStatus();
            } catch (Throwable t) {
                if (exitCode >= 0) {
                    status = new ScriptStatus(exitCode);
                }
                // Many of the unit tests fire exceptions inside exit.
                status = new ScriptStatus(t);
            }
        }

        closeCloseables();
        try {
            OutputStream stdout = getStdout();
            if (stdout != System.out) {
                stdout.close();
            }
            OutputStream stderr = getStderr();
            if (stderr != System.err) {
                stderr.close();
            }
        } catch (IOException ignore) {
        }

        return status;
    }

    @SuppressWarnings("unused")
    public String[] getArgv() {
        return args;
    }

    private ScriptStatus mainLoop()
        throws IOException
    {
        // Exit if there's no work do to but only if we're not pinned by a module.
        // We might exit if there are events on the timer queue if they are not also pinned.
        while (!tickFunctions.isEmpty() || (pinCount.get() > 0) || needTickCallback || needImmediateCallback) {
            try {
                if ((future != null) && future.isCancelled()) {
                    return ScriptStatus.CANCELLED;
                }

                // Call tick functions scheduled by process.nextTick. Node.js docs for
                // process.nextTick say that these things run before anything else in the event loop
                executeNextTicks();

                // Call tick functions scheduled by Java code.
                executeTicks();

                // If necessary, call into the timer module to fire all the tasks set up with "setImmediate."
                // Again, like regular Node, the docs say that these run before all I/O activity and all timers.
                executeImmediateCallbacks();

                // Calculate how long we will wait in the call to select, taking into consideration
                // what is on the timer queue and if there are pending ticks or immediate tasks.
                long now = System.currentTimeMillis();
                long pollTimeout;
                if (!tickFunctions.isEmpty() || needImmediateCallback || needTickCallback || (pinCount.get() == 0)) {
                    // Immediate work -- need to keep spinning
                    // Also keep spinning if we have no reason to keep the loop open
                    pollTimeout = 0L;
                } else if (timerQueue.isEmpty()) {
                    pollTimeout = DEFAULT_DELAY;
                } else {
                    Activity nextActivity = timerQueue.peek();
                    pollTimeout = (nextActivity.timeout - now);
                }

                // Check for network I/O and also sleep if necessary.
                // Any new timer or tick will wake up the selector immediately
                if (pollTimeout > 0L) {
                    if (log.isDebugEnabled()) {
                        log.debug("mainLoop: sleeping for {} pinCount = {}", pollTimeout, pinCount.get());
                    }
                    selector.select(pollTimeout);
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("mainLoop: not sleeping");
                    }
                    selector.selectNow();
                }

                // Fire any selected I/O functions
                executeNetworkCallbacks();

                // Check the timer queue for all expired timers
                executeTimerTasks(now);

            } catch (NodeExitException ne) {
                // This exception is thrown by process.exit()
                return ne.getStatus();
            } catch (Throwable t) {
                // All domain and process-wide error handling happened before we got here, so
                // if we get a RhinoException here, then we know that it is fatal.
                if (exitCode >= 0) {
                    return new ScriptStatus(exitCode);
                }
                return new ScriptStatus(t);
            }
        }
        return ScriptStatus.OK;
    }

    private boolean handleScriptException(Throwable se)
    {
        if (se instanceof NodeExitException) {
            return false;
        }
        if (exitCode >= 0) {
            return false;
        }

        // Stop script timing before we run this, so that we don't end up timing out the script twice!
        endTiming();

        if (handleFatal == null) {
            return false;
        }

        if (log.isDebugEnabled()) {
            log.debug("Handling fatal exception {}", se);
        }

        Object error = convertError(se);
        boolean handled = handleFatal.apply(error);
        if (log.isDebugEnabled()) {
            log.debug("Handled = {}", handled);
        }
        return handled;
    }

    /**
     * Execute ticks as defined by process.nextTick() and anything put on the queue from Java code.
     * Each one is timed separately, and error handling is done in here
     * so that we fire other things in the loop (such as timers) in the event of an error.
     */
    public void executeTicks()
    {
        Activity nextCall;
        do {
            nextCall = tickFunctions.poll();
            if (nextCall != null) {
                boolean timing = startTiming();
                try {
                    nextCall.execute();
                } catch (Throwable t) {
                    boolean handled = handleScriptException(t);
                    if (!handled) {
                        throw t;
                    } else {
                        // We can't keep looping here, because all these errors could cause starvation.
                        // Let timers and network I/O run instead.
                        return;
                    }
                } finally {
                    if (timing) {
                        endTiming();
                    }
                }
            }
        } while (nextCall != null);
    }

    /**
     * Execute everything set up by nextTick()
     */
    private void executeNextTicks()
    {
        if (needTickCallback) {
            if (log.isTraceEnabled()) {
                log.trace("Executing ticks");
            }
            boolean timed = startTiming();
            try {
                // We expect trireme.js to call us back the next time it has ticks
                needTickCallback = false;
                tickFromSpinner.run();
            } catch (Throwable t) {
                boolean handled = handleScriptException(t);
                if (!handled) {
                    throw t;
                }
            } finally {
                if (timed) {
                    endTiming();
                }
            }
        }
    }

    /**
     * Execute everything set up by setImmediate().
     */
    private void executeImmediateCallbacks()
    {
        if (needImmediateCallback) {
            if (log.isTraceEnabled()) {
                log.trace("Executing immediate tasks");
            }
            boolean timed = startTiming();
            try {
                // Timer module will automatically clear the flag
                immediateCallback.run();
            } catch (Throwable t) {
                boolean handled = handleScriptException(t);
                if (!handled) {
                    throw t;
                }
            } finally {
                if (timed) {
                    endTiming();
                }
            }
        }
    }

    /**
     * Execute everything that the selector has told is is ready.
     */
    private void executeNetworkCallbacks()
    {
        Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
        while (keys.hasNext()) {
            SelectionKey selKey = keys.next();
            boolean timed = startTiming();
            try {
                ((SelectorHandler)selKey.attachment()).selected(selKey);
            } catch (Throwable t) {
                boolean handled = handleScriptException(t);
                if (!handled) {
                    throw t;
                }
            } finally {
                if (timed) {
                    endTiming();
                }
            }
            keys.remove();
        }
    }

    private void executeTimerTasks(long now)
    {
        Activity timed = timerQueue.peek();
        while ((timed != null) && (timed.timeout <= now)) {
            timerQueue.poll();
            if (!timed.cancelled) {
                boolean timing = startTiming();
                try {
                    if (log.isDebugEnabled()) {
                        log.debug("Executing timer {}", timed.id);
                    }
                    timed.execute();
                } catch (Throwable t) {
                    boolean handled = handleScriptException(t);
                    if (!handled) {
                        throw t;
                    }
                } finally {
                    if (timing) {
                        endTiming();
                    }
                }
                if (timed.repeating && !timed.cancelled) {
                    timed.timeout = now + timed.interval;
                    if (log.isDebugEnabled()) {
                        log.debug("Re-registering {} to fire at {}", timed.id, timed.timeout);
                    }
                    timerQueue.add(timed);
                }
            }
            timed = timerQueue.peek();
        }
    }

    /**
     * One-time initialization of the built-in modules and objects.
     */
    private void initGlobals()
        throws NodeException
    {
        // As part of bootstrapping, we need to supply a "module" object for "process" to set up for us
        JSObject bootstrapModule = new DefaultScriptObject();
        JSObject exports = new DefaultScriptObject();
        bootstrapModule.setMember("exports", exports);

        // Bootstrap the whole thing with "process," which is our own internal JS/Java code
        // This is implemented by the "process" internal module in each node implementation
        Object exp = initializeModule("process", true, null, bootstrapModule, exports, "process.js");
        try {
            process = (JSObject)invocableEngine.invokeMethod(exp, "createProcess",
                                                             new Object[] { this });
        } catch (ScriptException|NoSuchMethodException e) {
            throw new NodeException(e);
        }

        engine.getBindings(ScriptContext.ENGINE_SCOPE).put("global", new DefaultScriptObject());

        // The buffer module needs special handling because of the "charsWritten" variable
        // TODO
        //buffer = (Buffer.BufferModuleImpl)require("buffer", cx);
    }

    /**
     * Initialize a native module implemented in Java or JavaScript code.
     */
    @SuppressWarnings("unused")
    public Object initializeModule(String modName, boolean internal,
                                   Object require, Object module, Object exports, String fileName)
    {
        NodeModule mod = registry.getJavaModule(modName, internal);
        if (mod != null) {
            return mod.getExports(this);
        }

        String source = registry.getModuleSource(modName, internal);
        if (source == null) {
            return null;
        }
        try {
            engine.eval(registry.wrapSource(source, modName, modName));
        } catch (ScriptException se) {
            throw new NodeException("Error initializing module: " + se, se);
        }

        try {
            return invocableEngine.invokeFunction('_' + modName + "Module", require, module, exports, fileName);
        } catch (ScriptException|NoSuchMethodException e) {
            throw new NodeException(e);
        }
    }

    /**
     * Only return a module implemented in Java.
     */
    public Object getJavaModule(String modName, boolean internal)
    {
       NodeModule mod = registry.getJavaModule(modName, internal);
        if (mod == null) {
            return null;
        }
        return mod.getExports(this);
    }

    /**
     * Simply return a native module's source code for direct loading into the script engine.
     */
    @SuppressWarnings("unused")
    public String getModuleSource(String modName, boolean internal)
    {
        return registry.getModuleSource(modName, internal);
    }

    /**
     * This is where we load native modules, which are loaded by "require".
     */
    @SuppressWarnings("unused")
    public boolean isNativeModule(String name)
        throws IllegalAccessException, InstantiationException
    {
        return (registry.getJavaModule(name, false) != null) ||
               (registry.getModule(name, engine, false) != null);
    }

    private boolean startTiming()
    {
        /* Here is where we could time script execution and stop it -- not supported in Nashorn
        if (env != null) {
            long tl = env.getScriptTimeLimit();
            if (tl > 0L) {
                cx.putThreadLocal(TIMEOUT_TIMESTAMP_KEY, System.currentTimeMillis() + tl);
                return true;
            }
        }
        */
        return false;
    }

    private void endTiming()
    {
        // Not suported in Nashorn yet
        //cx.removeThreadLocal(TIMEOUT_TIMESTAMP_KEY);
    }

    public abstract class Activity
        implements Comparable<Activity>
    {
        protected int id;
        protected long timeout;
        protected long interval;
        protected boolean repeating;
        protected boolean cancelled;
        protected Object domain;

        abstract void execute();

        int getId() {
            return id;
        }

        void setId(int id) {
            this.id = id;
        }

        public long getTimeout() {
            return timeout;
        }

        public void setTimeout(long timeout) {
            this.timeout = timeout;
        }

        public long getInterval() {
            return interval;
        }

        public void setInterval(long interval) {
            this.interval = interval;
        }

        public boolean isRepeating() {
            return repeating;
        }

        public void setRepeating(boolean repeating) {
            this.repeating = repeating;
        }

        public boolean isCancelled() {
            return cancelled;
        }

        public void setCancelled(boolean cancelled) {
            this.cancelled = cancelled;
        }

        public Object getDomain() {
            return domain;
        }

        public void setDomain(Object domain) {
            this.domain = domain;
        }

        @Override
        public int compareTo(Activity a)
        {
            if (timeout < a.timeout) {
                return -1;
            }
            if (timeout > a.timeout) {
                return 1;
            }
            return 0;
        }
    }

    private final class Callback
        extends Activity
    {
        JSObject function;
        Object thisObj;
        Object[] args;

        Callback(JSObject f, Object thisObj, Object[] args)
        {
            this.function = f;
            this.thisObj = thisObj;
            this.args = args;
        }

        /**
         * Submit the tick, with support for domains handled in JavaScript.
         * This is also necessary because not everything that we do is a "top level function" in JS
         * and we cannot invoke those functions directly from Java code.
         */
        @Override
        void execute()
        {
            Object[] callArgs =
                new Object[(args == null ? 0 : args.length) + 3];
            callArgs[0] = function;
            callArgs[1] = thisObj;
            callArgs[2] = domain;
            if (args != null) {
                System.arraycopy(args, 0, callArgs, 3, args.length);
            }
            // Submit in the scope of "function"
            // pass "this" and the args to "submitTick," which will honor them
            submitTick.call(process, callArgs);
        }
    }

    private final class Task
        extends Activity
    {
        private ScriptTask task;

        Task(ScriptTask task)
        {
            this.task = task;
        }

        @Override
        void execute()
        {
            /* TODO domain stuff!
            if (domain != null) {
                if (ScriptableObject.hasProperty(domain, "_disposed")) {
                    domain = null;
                }
            }
            if (domain != null) {
                if (log.isDebugEnabled()) {
                    log.debug("Entering domain {}", System.identityHashCode(domain));
                }
                Function enter = (Function)ScriptableObject.getProperty(domain, "enter");
                enter.call(cx, enter, domain, new Object[0]);
            }
            */

            task.execute();

            // Do NOT do this next bit in a try..finally block. Why not? Because the exception handling
            // logic in runMain depends on "process.domain" still being set, and it will clean up
            // on failure there.
            /*
            if (domain != null) {
                if (log.isDebugEnabled()) {
                    log.debug("Exiting domain {}", System.identityHashCode(domain));
                }
                Function exit = (Function)ScriptableObject.getProperty(domain, "exit");
                exit.call(cx, exit, domain, new Object[0]);
            }
            */
        }
    }

    private final class RunnableTask
        extends Activity
        implements Future<Boolean>
    {
        private Runnable task;

        RunnableTask(Runnable task)
        {
            this.task = task;
        }

        @Override
        protected void execute()
        {
            task.run();
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning)
        {
            setCancelled(true);
            return true;
        }

        @Override
        public boolean isDone()
        {
            return false;
        }

        @Override
        public Boolean get()
        {
            return Boolean.TRUE;
        }

        @Override
        public Boolean get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException
        {
            return Boolean.TRUE;
        }
    }
}
