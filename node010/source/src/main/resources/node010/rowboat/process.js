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
/*
 * This is an implementation of the part of the "process" object from Node.js that is
 * implemented in native code. Here instead, we implement it in Nashorn JavaScript with
 * direct calls to the appropriate Java stuff.
 */

var System =                 Java.type('java.lang.System');

var ConsoleHandle =          Java.type('io.apigee.rowboat.handles.ConsoleHandle');
var JavaInputStreamHandle  = Java.type('io.apigee.rowboat.handles.JavaInputStreamHandle');
var JavaOutputStreamHandle = Java.type('io.apigee.rowboat.handles.JavaOutputStreamHandle');
var NodeExitException =      Java.type('io.apigee.rowboat.internal.NodeExitException');
var Version =                Java.type('io.apigee.rowboat.internal.Version');

var NANO = 100000000;
var TITLE = "rowboat";
var PLATFORM = "java";
var EXECUTABLE = "./node";

function Process(runtime) {
  this._runtime = runtime;
  this._bindingCache = {};
  this._tickInfoBox = [];
  this._tickInfoBox[0] = null;
  this._tickInfoBox[1] = null;
  this._tickInfoBox[2] = null;
  this._startTime = System.currentTimeMillis();
  this.moduleLoadList = [];

  this.title = TITLE;
  this.platform = PLATFORM;
  this.execPath = EXECUTABLE;

  // Set up stuff that comes from the runtime environment
  var script = runtime.getScriptObject();
  if (script.getSource() != null) {
    this._eval = script.getSource();
  }
  this._print_eval = script.isPrintEval();
  this._forceRepl = script.isForceRepl();
  this.throwDeprecation = !script.isNoDeprecation();
  this.traceDeprecation = script.isTraceDeprecation();

  Object.defineProperty(this, "env", {
    get: this.getEnv
  });
  Object.defineProperty(this, "version", {
    get: this.getVersion
  });
  Object.defineProperty(this, "versions", {
    get: this.getVersions
  });
  Object.defineProperty(this, "arch", {
    get: this.getArch
  });
  Object.defineProperty(this, "argv", {
    get: this.getArgv
  });

  Object.defineProperty(this, "_stdoutHandle", {
    get: this.getStdoutHandle
  });
  Object.defineProperty(this, "_stdinHandle", {
    get: this.getStdinHandle
  });
  Object.defineProperty(this, "_stderrHandle", {
    get: this.getStderrHandle
  });

  Object.defineProperty(this, "_needImmediateCallback", {
    get: this.getNeedImmediateCallback,
    set: this.setNeedImmediateCallback
  });
  Object.defineProperty(this, "_immediateCallback", {
    get: this.getImmediateCallback,
    set: this.setImmediateCallback
  });
  Object.defineProperty(this, "_fatalException", {
    get: this.getHandleFatal,
    set: this.setHandleFatal
  });
  Object.defineProperty(this, "_tickFromSpinner", {
    get: this.getTickFromSpinner,
    set: this.setTickFromSpinner
  });

  // TODO config
  // TODO features
}

// Rowboat note: Nashorn really wants to call a function on our object but only if it comes
// from JavaScript and not from the JSObject interface, so do this:
module.exports = {};

module.exports.createProcess = function(runtime) {
  return new Process(runtime);
};

Process.prototype.getRuntime = function() {
  return this._runtime;
}

Process.prototype.getEnv = function() {
  if (this._env) {
    return this._env;
  }

  this._env = {};
  var e = this._runtime.getScriptObject().getEnvironment();
  var i = e.entrySet().iterator();
  while (i.hasNext()) {
    var entry = i.next();
    this._env[entry.getKey()] = entry.getValue();
  }
  return this._env;
};

Process.prototype.getArgv = function() {
  return this._runtime.getArgv();
};

Process.prototype.getVersion = function() {
  return "v" + Version.NODE_VERSION;
};

Process.prototype.getVersions = function() {
  return {
    rowboat: Version.ROWBOAT_VERSION,
    ssl: Version.SSL_VERSION,
    node: getRuntime().getNodeVersion()
  };
};

Process.prototype.getArch = function() {
  var arch = System.getProperty("os.arch");
  return (arch === 'x86_64' ? 'x64' : 'ia32');
};

function createStreamHandle(handle) {
  var wrap = process.binding('stream_wrap');
  return new wrap.Stream(handle);
}

function createConsoleHandle(handle) {
  var wrap = process.binding('console_wrap');
  return new wrap.Console(handle);
}

Process.prototype.getStdoutHandle = function() {
  var streamHandle;
  if ((this._runtime.getStdout() == System.out) && ConsoleHandle.isConsoleSupported()) {
    streamHandle = new ConsoleHandle(this._runtime);
    return createConsoleHandle(streamHandle);
  } else {
    streamHandle = new JavaOutputStreamHandle(this._runtime.getStdout(), this._runtime);
    return createStreamHandle(streamHandle);
  }
};

Process.prototype.getStdinHandle = function() {
  var streamHandle;
  if ((this._runtime.getStdin() == System.in) && ConsoleHandle.isConsoleSupported()) {
    streamHandle = new ConsoleHandle(this._runtime);
    return createConsoleHandle(streamHandle);
  } else {
    streamHandle = new JavaInputStreamHandle(this._runtime.getStdin(), this._runtime);
    return createStreamHandle(streamHandle);
  }
};

Process.prototype.getStderrHandle = function() {
  var streamHandle = new JavaOutputStreamHandle(this._runtime.getStderr(), this._runtime);
  return createStreamHandle(streamHandle);
};

Process.prototype.setSubmitTick = function(submit) {
  this._runtime.setSubmitTick(submit);
};

Process.prototype.binding = function(module) {
  if (this._bindingCache[module]) {
    return this._bindingCache[module].exports;
  }

  var mod = { exports: {} };
  this._bindingCache[module] = mod;
  mod.exports = this._runtime.initializeModule(module, true, this._nativeModule.require,
                                               mod, mod.exports, module + '.js');
  mod.loaded = true;
  return mod.exports;
};

Process.prototype.isNativeModule = function(name) {
  return this._runtime.isNativeModule(name);
};

Process.prototype.getNativeModule = function(name, require, module, fileName) {
  if (!module.exports) {
    module.exports = {};
  }
  return this._runtime.initializeModule(name, false, require, module, module.exports, fileName);
};

Process.prototype.abort = function() {
  throw new NodeExitException(NodeExitException.Reason.FATAL);
};

Process.prototype.chdir = function(dir) {
  this._runtime.setWorkingDirectory(dir);
};

Process.prototype.cwd = function() {
  return this._runtime.getWorkingDirectory();
};

Process.prototype.reallyExit = function(code) {
  var realCode = (code ? code : 0);
  throw new NodeExitException(NodeExitException.Reason.NORMAL, realCode);
};

Process.prototype._kill = function(pid, signal) {
  throw new Error('Not yet implemented');
};

Process.prototype.send = function(msg) {
  throw new Error('Not yet implemented');
};

Process.prototype.memoryUsage = function() {
  var runtime = Java.type('java.lang.Runtime');
  return {
    rss: runtime.totalMemory(),
    heapTotal: runtime.maxMemory(),
    heapUsed: runtime.totalMemory()
  };
};

Process.prototype._needTickCallback = function() {
  this._runtime.setNeedTickCallback(true);
};

Process.prototype.setNeedImmediateCallback = function(need) {
  this._runtime.setNeedImmediateCallback(need);
};

Process.prototype.getNeedImmediateCallback = function() {
  return this._runtime.getNeedImmediateCallback();
};

Process.prototype.setImmediateCallback = function(need) {
  this._runtime.setImmediateCallback(need);
};

Process.prototype.getImmediateCallback = function() {
  return this._runtime.getImmediateCallback();
};

Process.prototype.setHandleFatal = function(handle) {
  this._runtime.setHandleFatal(handle);
};

Process.prototype.getHandleFatal = function() {
  return this._runtime.getHandleFatal();
};

Process.prototype.setTickFromSpinner = function(f) {
  this._runtime.setTickFromSpinner(f);
};

Process.prototype.getTickFromSpinner = function() {
  return this._runtime.getTickFromSpinner();
};

Process.prototype.umask = function(newMask) {
  if (newMask) {
    var oldMask = this._runtime.getUmask();
    this._runtime.setUmask(parseInt(newMask));
    return oldMask;
  }
  return this._runtime.getUmask();
};

Process.prototype.uptime = function() {
  return (System.currenTimeMillis() - this._startTime) / 1000;
};

Process.prototype.hrtime = function(time) {
  var nanos = System.nanoTime();
  if (time) {
    var startSecs = time[0];
    var startNs = time[1];
    var startNanos = ((startSecs * NANO) + startNs);
    nanos -= startNanos;
  }

  var ret = [];
  ret[0] = nanos / NANO;
  ret[1] = nanos % NANO;
  return ret;
};
