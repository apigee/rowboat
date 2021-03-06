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

var Stream = process.binding('stream_wrap').Stream;
var util = require('util');

var NIOSocketHandle = Java.type('io.apigee.trireme.kernel.handles.NIOSocketHandle');
var Inet6Address = Java.type('java.net.Inet6Address');

var debug;
if (process.env.NODE_DEBUG && /net/.test(process.env.NODE_DEBUG)) {
  var pid = process.pid;
  debug = function(x) {
    // if console is not set up yet, then skip this.
    if (!console.error)
      return;
    console.error('TCP_WRAP: %d', pid,
                  util.format.apply(util, arguments).slice(0, 500));
  };
} else {
  debug = function() { };
}

var lastId = 0;

function TCP(h) {
  if (!(this instanceof TCP)) {
    return new TCP(h);
  }

  this.id = lastId++;
  var handle = (h ? h : new NIOSocketHandle(process.getRuntime()));
  Stream.call(this, handle);

  // Unlike other types of handles, every open socket "pins" the server explicitly and keeps it
  // running until it is either closed or "unref" is called.
  this.ref();
}
module.exports.TCP = TCP;
util.inherits(TCP, Stream);

TCP.prototype.toString = function() {
  return 'TCP handle ' + this.id;
};

function bind(address, port) {
  try {
    this.handle.bind(address, port);
    process._errno = 0;
    return undefined;
  } catch (e) {
    process._errno = process.getJavaErrno(e);
    return process._errno;
  }
}
TCP.prototype.bind = bind;
TCP.prototype.bind6 = bind;

TCP.prototype.listen = function(backlog) {
  try {
    debug(this.id + ': listen');
    var self = this;
    this.handle.listen(backlog, function(err, sockHandle) {
      if (!err) {
        // Nothing to do on an uncaught accept error right now
        onConnection(self, sockHandle);
      }
    });
    process._errno = 0;
    return undefined;
  } catch (e) {
    process._errno = process.getJavaErrno(e);
    return process._errno;
  }
};

function onConnection(self, sockHandle) {
  debug(self.id + ': onConnection. self = ' + self);
  if (self.onconnection) {
    var newHandle = new TCP(sockHandle);
    self.onconnection.call(self, newHandle);
  }
}

function connect(host, port) {
  var req = {};
  try {
    debug(this.id + ': connect');
    var self = this;
    this.handle.connect(host, port, function(errCode) {
      onConnectComplete(self, req, errCode);
    });
    process._errno = 0;
    return req;
  } catch (e) {
    process._errno = process.getJavaErrno(e);
    return null;
  }
}
TCP.prototype.connect = connect;
TCP.prototype.connect6 = connect;

function onConnectComplete(self, req, errCode) {
  debug(self.id + ': onConnectComplete self = ' + self);
  if (req.oncomplete) {
    // net.js expects an integer error code this time, not a string
    if (errCode !== 0) {
      process._errno = errCode;
    }
    req.oncomplete.call(self, errCode, self, req, true, true);
  }
}

TCP.prototype.shutdown = function() {
  var req = {
    _handle: this
  };
  var self = this;
  try {
    this.handle.shutdown(function(errCode) {
      onShutdownComplete(self, req, errCode);
    });
    process._errno = 0;
    return req;
  } catch (e) {
    debug('Error on shutdown: %j', e);
    process._errno = process.getJavaErrno(e);
    return null;
  }
};

function onShutdownComplete(self, req, errCode) {
  // This version of Node expects to set "oncomplete" only after write returns.
  setImmediate(function() {
    if (req.oncomplete) {
      var err = process.convertJavaErrno(errCode);
      req.oncomplete.call(self, err, req._handle, req);
    }
  });
}

function convertAddress(m) {
  // TODO getHostString, perhaps with instanceof?
  return {
    port: m.getPort(),
    address: m.getHostString(),
    family: (m.getAddress() instanceof Inet6Address ? 'IPv6' : 'IPv4')
  };
}

TCP.prototype.getsockname = function() {
  return convertAddress(this.handle.getSockName());
};

TCP.prototype.getpeername = function() {
  return convertAddress(this.handle.getPeerName());
};

TCP.prototype.setNoDelay = function(no) {
  try {
    this.handle.setNoDelay(no);
    process._errno = 0;
  } catch (e) {
    process._errno = process.getJavaErrno(e);
  }
};

TCP.prototype.setKeepAlive = function(no) {
  try {
    this.handle.setKeepAlive(no);
    process._errno = 0;
  } catch (e) {
    process._errno = process.getJavaErrno(e);
  }
};