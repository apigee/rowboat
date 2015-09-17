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

var constants = require('constants');
var util = require('util');

var Referenceable = process.binding('referenceable').Referenceable;
var JavaStream = process.binding('stream_wrap').Stream;

var StringArray = Java.type('java.lang.String[]');
var ProcessTable = Java.type('io.apigee.rowboat.process.ProcessTable').get();
var ProcessInfo = Java.type('io.apigee.rowboat.process.ProcessInfo');
var SpawnedOSProcess = Java.type('io.apigee.rowboat.process.SpawnedOSProcess');
var SpawnedRowboatProcess = Java.type('io.apigee.rowboat.process.SpawnedRowboatProcess');

var debug;
var isDebug;
if (process.env.NODE_DEBUG && /child_process/.test(process.env.NODE_DEBUG)) {
  debug = function(x) { console.error('child_process: %s', x); };
  isDebug = true;
} else {
  debug = function() { };
}

function Process() {
  Referenceable.call(this);

  Object.defineProperty(this, "connected", {
    get: this.isConnected
  });
}
util.inherits(Process, Referenceable);

function createProcess() {
  return new Process();
}
module.exports.createProcess = createProcess;

Process.prototype.close = function() {
  this.unref();
  if (this.process) {
    this.process.close();
  }
};

Process.prototype.spawn = function(opts) {
  if (!opts.args || (opts.args.length < 1)) {
    throw new Error(constants.EINVAL);
  }

  // TODO check sandbox

  this.pid = ProcessTable.getNextPid();

  var javaOpts = processOpts(opts);

  if (isSpawningNode(opts.args[0])) {
    this.process = new RowboatProcess(this, opts, javaOpts);
  } else {
    this.process = new OSProcess(this, opts, javaOpts);
  }

  try {
    this.ref();
    this.process.spawn();
    ProcessTable.add(this.pid, process);
  } catch (e) {
    return process.checkJavaErrno(e);
  }
  return undefined;
};

function isSpawningNode(n) {
  return ((n === 'node') || (n === './node') || (n === process.argv[0]));
}

function processOpts(opts) {
  var jo = new ProcessInfo();
  if (!opts.args || (opts.args.length < 1)) {
    throw new Error(constants.EINVAL);
  }
  var args = new StringArray(opts.args.length);
  for (var i in opts.args) {
    args[i] = opts.args[i];
  }
  jo.setArgs(args);

  if (!opts.stdio || (opts.stdio.length < 3)) {
    throw new Error(constants.EINVAL);
  }

  setStdio(jo, opts.stdio[0], 0);
  setStdio(jo, opts.stdio[1], 1);
  setStdio(jo, opts.stdio[2], 2);

  if (opts.cwd) {
    jo.setCwd(opts.cwd);
  }

  // TODO env pairs!

  return jo;
}

function setStdio(jo, stdio, i) {
  var fd = ((stdio.fd !== undefined) ? stdio.fd : -1);
  if (isDebug) {
    debug(util.format('Stdio %d type = %s fd = %d', i, stdio.type, fd));
  }
  jo.setStdio(i, stdio.type, fd);
}

Process.prototype.kill = function(signal) {
  if (this.process) {
    this.process.terminate(signal);
  }
};

Process.prototype.send = function(msg) {
  throw new Error('Not implemented');
};

Process.prototype.disconnect = function() {
  throw new Error('Not implemented');
};

Process.prototype.isConnected = function() {
  return (this.process && this.process.connected);
};

Process.prototype._handleExit = function(exitCode) {
  ProcessTable.remove(this.pid);
  if (this.onexit) {
    this.onexit(exitCode, 0);
  }
};

function terminateChild(signal) {
  if (this.proc) {
    this.proc.terminate(signal);
  }
}

function closeChild() {
  if (this.proc) {
    this.proc.close();
  }
}

function OSProcess(parent, opts, javaOpts) {
  this.parent = parent;
  this.opts = opts;
  this.javaOpts = javaOpts;
  this.proc = new SpawnedOSProcess(process.getRuntime());
}

OSProcess.prototype.spawn = function() {
  var self = this;
  this.proc.spawn(this.javaOpts, function(exitCode) {
    self.parent._handleExit(exitCode);
  });

  for (var i = 0; i < 3; i++) {
    if (this.javaOpts.getStdioHandle(i)) {
      this.opts.stdio[i].handle = new JavaStream(this.javaOpts.getStdioHandle(i));
    }
  }
};

OSProcess.prototype.terminate = terminateChild;
OSProcess.prototype.close = closeChild;

function RowboatProcess(parent, opts, javaOpts) {
  this.parent = parent;
  this.opts = opts;
  this.javaOpts = javaOpts;
  this.proc = new SpawnedRowboatProcess(process.getRuntime());
}

RowboatProcess.prototype.spawn = function() {
  var self = this;
  this.proc.spawn(this.javaOpts, function(exitCode) {
    self.parent._handleExit(exitCode);
  });

  for (var i = 0; i < 3; i++) {
    if (this.javaOpts.getStdioHandle(i)) {
      this.opts.stdio[i].handle = new JavaStream(this.javaOpts.getStdioHandle(i));
    }
  }
};

RowboatProcess.prototype.terminate = terminateChild;
RowboatProcess.prototype.close = closeChild;
