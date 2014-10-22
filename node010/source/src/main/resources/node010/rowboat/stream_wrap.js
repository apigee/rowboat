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

var Charsets = Java.type('io.apigee.trireme.kernel.Charsets');

var Referenceable = process.binding('referenceable').Referenceable;
var util = require('util');

var debug;
if (process.env.NODE_DEBUG && /net/.test(process.env.NODE_DEBUG)) {
  var pid = process.pid;
  debug = function(x) {
    // if console is not set up yet, then skip this.
    if (!console.error)
      return;
    console.error('STREAM_WRAP: %d', pid,
                  util.format.apply(util, arguments).slice(0, 500));
  };
} else {
  debug = function() { };
}

function Stream(handle) {
  if (!(this instanceof Stream)) {
    return new Stream(handle);
  }

  // The handle is a Java object -- make it non-enumerable or debugging will break
  Object.defineProperty(this, 'handle', {
    value: handle
  });

  this.bytes = 0;
  Referenceable.call(this);

  Object.defineProperty(this, "writeQueueSize", {
    get: this.getWriteQueueSize
  });
}
module.exports.Stream = Stream;
util.inherits(Stream, Referenceable);

Stream.prototype.getWriteQueueSize = function() {
  return this.handle.getWritesOutstanding();
};

Stream.prototype.close = function(cb) {
  Referenceable.prototype.close.call(this);
  this.handle.close();
  if (cb) {
    setImmediate(cb);
  }
};

Stream.prototype.writeBuffer = function(buf) {
  var req = {
    _handle: this
  };
  var self = this;
  var len = this.handle.write(buf.toJava(), function(errCode) {
    onWriteComplete(self, req, errCode);
  });
  // net.js expects that we will update "bytes" before the callback is called.
  req.bytes = len;
  this.bytes += len;
  return req;
};

Stream.prototype.writeUtf8String = function(s) {
  return writeString(this, s, Charsets.UTF8);
};

Stream.prototype.writeUcs2String = function(s) {
  return writeString(this, s, Charsets.UCS2);
};

Stream.prototype.writeAsciiString = function(s) {
  return writeString(this, s, Charsets.ASCII);
};

function writeString(self, s, cs) {
  var req = {
    _handle: self
  };
  var len = self.handle.write(s, cs, function(errCode) {
    onWriteComplete(self, req, errCode);
  });
  req.bytes = len;
  self.bytes += len;
  return req;
}

function onWriteComplete(self, req, err) {
  // This version of Node expects to set "oncomplete" only after write returns.
  setImmediate(function() {
    if (req.oncomplete) {
      req.oncomplete.call(self, process.convertJavaErrno(err), req._handle, req);
    }
  });
}

Stream.prototype.readStart = function() {
  var self = this;
  this.handle.startReading(function(err, buf) {
    onReadComplete(self, err, buf);
  });
};

Stream.prototype.readStop = function() {
  this.handle.stopReading();
};

function onReadComplete(self, err, javaBuf) {
  if (self.onread) {
    process._errno = (err ? process.convertJavaErrno(err) : 0);
    if (javaBuf) {
      var buf = Buffer.fromJava(javaBuf);
      self.onread.call(self, buf, 0, buf.length);
    } else {
      self.onread.call(self, null, 0, 0);
    }
  }
}
