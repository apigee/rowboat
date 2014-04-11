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

var Charsets = Java.type('io.apigee.rowboat.internal.Charsets');

var Referenceable = require('referenceable').Referenceable;
var util = require('util');

function Stream(handle) {
  if (!(this instanceof Stream)) {
    return new Stream(handle);
  }

  this.handle = handle;
  this.bytes = 0;
  Referenceable.call(this);

  Object.defineProperty(this, "writeQueueSize", {
    get: this.getWriteQueueSize
  });
}
module.exports.Stream = Stream;
util.inherits(Stream, Referenceable);

Stream.prototype.getWriteQueueSize = function() {
  return handle.getWritesOutstanding();
};

Stream.prototype.close = function(cb) {
  Referenceable.close.call(this);
  this.handle.close();
  unref();
  if (cb) {
    setImmediate(cb);
  }
};

Stream.prototype.writeBuffer = function(buf) {
  var req = {};
  var len = this.handle.write(buf, req);
  req.bytes = len;
  this.bytes += len;
  return req;
  // TODO pass callback for oncomplete
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
  var req = {};
  var len = self.handle.write(s, cs, req);
  req.bytes = len;
  self.bytes += len;
  return req;
}

Stream.prototype.readStart = function() {
  // TODO set up callbacks so that they call "onread"
};

Stream.prototype.readStop = function() {
  // TODO don't worry about the callbacks
};
