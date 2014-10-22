/*
 * Implement the internal "slowbuffer" module. This will use Java code where necessary, including to
 * store the internal byte buffer.
 */

var BufferUtils = Java.type('io.apigee.rowboat.node010.BufferUtils');
var ByteBuffer = Java.type('java.nio.ByteBuffer');
var Charsets = Java.type('io.apigee.trireme.kernel.Charsets');

var bufUtils = BufferUtils.get();

function SlowBuffer(lengthOrBuffer) {
  if (!(this instanceof SlowBuffer)) {
    return new SlowBuffer(lengthOrBuffer);
  }

  var buf;
  if (typeof lengthOrBuffer === 'number') {
    buf = ByteBuffer.allocate(lengthOrBuffer)
  } else {
    buf = lengthOrBuffer;
  }

  Object.defineProperty(this, '_buf', {
    value: buf,
    writable: false,
    enumerable: false
  });

  // This is a mostly-undocumented Nashorn feature that lets the array indexing of this object go to the index
  Object.setIndexedPropertiesToExternalArrayData(this, buf);
  this.length = buf.limit();
}
module.exports.SlowBuffer = SlowBuffer;

/*
 * This is called from buffer.js to set the internal representation pointing to the native code.
 */
SlowBuffer.makeFastBuffer = function(parent, buf, offset, length) {
  if (offset > parent.length) {
    throw new RangeError('offset out of range');
  }
  if ((offset + length) > parent.length) {
    throw new RangeError('length out of range');
  }
  if ((offset + length) < offset) {
    throw new RangeError('offset or length out of range');
  }

  var bb = bufUtils.sliceBuffer(parent._buf, offset, length);
  Object.setIndexedPropertiesToExternalArrayData(buf, bb);

  buf.toJava = function() {
    return bb;
  };
};

SlowBuffer._charsWritten = 0;

SlowBuffer.byteLength = function(str, encoding) {
  return bufUtils.getByteLength(str, encoding);
};

// Convert the buffer to a ByteBuffer that represents only its own content.
// Returns an object that can only be passed to Java code
SlowBuffer.prototype.toJava = function() {
  return this._buf;
};

SlowBuffer.prototype.fill = function(value, start, end) {
  checkSliceBounds(this._buf, start, end);
  bufUtils.fill(this._buf, value, start, end);
};

SlowBuffer.prototype.copy = function(target, targetStart, start, end) {
  if (end < start) {
    throw new RangeError('sourceEnd < sourceStart');
  }
  if (targetStart > target.length) {
    throw new RangeError('targetStart out of bounds');
  }
  if (start > this.length) {
    throw new RangeError('sourceStart out of bounds');
  }
  if (end > this._buf.limit()) {
    throw new RangeError('sourceEnd out of bounds');
  }
  return bufUtils.copy(this._buf, target._buf, targetStart, start, end);
};

// These return strings
SlowBuffer.prototype.hexSlice = function(start, end) {
  return stringSlice(this._buf, start, end, Charsets.NODE_HEX);
};

SlowBuffer.prototype.utf8Slice = function(start, end) {
  return stringSlice(this._buf, start, end, Charsets.UTF8);
};

SlowBuffer.prototype.asciiSlice = function(start, end) {
  return stringSlice(this._buf, start, end, Charsets.ASCII);
};

SlowBuffer.prototype.binarySlice = function(start, end) {
  return stringSlice(this._buf, start, end, Charsets.NODE_BINARY);
};

SlowBuffer.prototype.base64Slice = function(start, end) {
  return stringSlice(this._buf, start, end, Charsets.BASE64);
};

SlowBuffer.prototype.ucs2Slice = function(start, end) {
  return stringSlice(this._buf, start, end, Charsets.UCS2);
};

function stringSlice(buf, start, end, charset) {
  checkSliceBounds(buf, start, end);
  return bufUtils.toString(buf, start, end, charset);
}

function checkSliceBounds(buf, start, end) {
  if ((start < 0) || (end < 0)) {
    throw new TypeError('Bad argument');
  }
  if (!(start <= end)) {
    throw new Error('Must have start <= end');
  }
  if (end > buf.limit()) {
    throw new Error('end cannot be longer than parent.length');
  }
}

function updateCharsWritten(c) {
  SlowBuffer._charsWritten = c;
}

// These decode the strings and copy the bytes
SlowBuffer.prototype.hexWrite = function(str, offset, length) {
  if ((length % 2) !== 0) {
    throw new TypeError('Invalid hex string');
  }
  return bufUtils.write(this._buf, str, offset, length, Charsets.NODE_HEX, updateCharsWritten);
};

SlowBuffer.prototype.utf8Write = function(str, offset, length) {
  return stringWrite(this._buf, str, offset, length, Charsets.UTF8, updateCharsWritten);
};

SlowBuffer.prototype.asciiWrite = function(str, offset, length) {
  return stringWrite(this._buf, str, offset, length, Charsets.ASCII, updateCharsWritten);
};

SlowBuffer.prototype.binaryWrite = function(str, offset, length) {
  return stringWrite(this._buf, str, offset, length, Charsets.NODE_BINARY, updateCharsWritten);
};

SlowBuffer.prototype.base64Write = function(str, offset, length) {
  return stringWrite(this._buf, str, offset, length, Charsets.BASE64, updateCharsWritten);
};

SlowBuffer.prototype.ucs2Write = function(str, offset, length) {
  return stringWrite(this._buf, str, offset, length, Charsets.UCS2, updateCharsWritten);
};

function stringWrite(buf, str, offset, length, charset, cb) {


  return bufUtils.write(buf, str, offset, length, charset, cb);
}

SlowBuffer.prototype.readFloatLE = function(offset, noassert) {
  throw new Error('Not implemented');
};

SlowBuffer.prototype.readFloatBE = function(offset, noassert) {
  throw new Error('Not implemented');
};

SlowBuffer.prototype.readDoubleLE = function(offset, noassert) {
  throw new Error('Not implemented');
};

SlowBuffer.prototype.readDoubleBE = function(offset, noassert) {
  throw new Error('Not implemented');
};

SlowBuffer.prototype.writeFloatLE = function(value, offset, noassert) {
  throw new Error('Not implemented');
};

SlowBuffer.prototype.writeFloatBE = function(value, offset, noassert) {
  throw new Error('Not implemented');
};

SlowBuffer.prototype.writeDoubleLE = function(value, offset, noassert) {
  throw new Error('Not implemented');
};

SlowBuffer.prototype.writeDoubleBE = function(value, offset, noassert) {
  throw new Error('Not implemented');
};
