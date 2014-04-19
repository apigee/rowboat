// Copyright Joyent, Inc. and other Node contributors.
//
// Permission is hereby granted, free of charge, to any person obtaining a
// copy of this software and associated documentation files (the
// "Software"), to deal in the Software without restriction, including
// without limitation the rights to use, copy, modify, merge, publish,
// distribute, sublicense, and/or sell copies of the Software, and to permit
// persons to whom the Software is furnished to do so, subject to the
// following conditions:
//
// The above copyright notice and this permission notice shall be included
// in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
// OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
// MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
// NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
// DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
// OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE
// USE OR OTHER DEALINGS IN THE SOFTWARE.

/*
 * Rowboat:
 *   This class has been modified for Rowboat because of the way that Nashorn (currently) handles
 *   indexed properties.
 */

var JavaSlowBuffer = Java.type('io.apigee.rowboat.node010.classes.SlowBuffer');
var SlowBuffer = process.binding('buffer').SlowBuffer;
var assert = require('assert');

exports.INSPECT_MAX_BYTES = 50;

function clamp(index, len, defaultValue) {
  if (typeof index !== 'number') return defaultValue;
  index = ~~index;  // Coerce to integer.
  if (index >= len) return len;
  if (index >= 0) return index;
  index += len;
  if (index >= 0) return index;
  return 0;
}


function toHex(n) {
  if (n < 16) return '0' + n.toString(16);
  return n.toString(16);
}

var slowProto = {};
var proto = {};

function constructSlowBuffer(b) {
  for (p in proto) {
    b[p] = proto[p];
  }
  for (p in slowProto) {
    b[p] = slowProto[p];
  }
}
JavaSlowBuffer.setConstructor(SlowBuffer, constructSlowBuffer);

slowProto.toString = function(encoding, start, end) {
  encoding = String(encoding || 'utf8').toLowerCase();
  start = +start || 0;
  if (typeof end !== 'number') end = this.length;

  // Fastpath empty strings
  if (+end == start) {
    return '';
  }

  switch (encoding) {
    case 'hex':
      return this.hexSlice(start, end);

    case 'utf8':
    case 'utf-8':
      return this.utf8Slice(start, end);

    case 'ascii':
      return this.asciiSlice(start, end);

    case 'binary':
      return this.binarySlice(start, end);

    case 'base64':
      return this.base64Slice(start, end);

    case 'ucs2':
    case 'ucs-2':
    case 'utf16le':
    case 'utf-16le':
      return this.ucs2Slice(start, end);

    default:
      throw new TypeError('Unknown encoding: ' + encoding);
  }
};


slowProto.write = function(string, offset, length, encoding) {
  // Support both (string, offset, length, encoding)
  // and the legacy (string, encoding, offset, length)
  if (isFinite(offset)) {
    if (!isFinite(length)) {
      encoding = length;
      length = undefined;
    }
  } else {  // legacy
    var swap = encoding;
    encoding = offset;
    offset = length;
    length = swap;
  }

  offset = +offset || 0;
  var remaining = this.length - offset;
  if (!length) {
    length = remaining;
  } else {
    length = +length;
    if (length > remaining) {
      length = remaining;
    }
  }
  encoding = String(encoding || 'utf8').toLowerCase();

  switch (encoding) {
    case 'hex':
      return this.hexWrite(string, offset, length);

    case 'utf8':
    case 'utf-8':
      return this.utf8Write(string, offset, length);

    case 'ascii':
      return this.asciiWrite(string, offset, length);

    case 'binary':
      return this.binaryWrite(string, offset, length);

    case 'base64':
      return this.base64Write(string, offset, length);

    case 'ucs2':
    case 'ucs-2':
    case 'utf16le':
    case 'utf-16le':
      return this.ucs2Write(string, offset, length);

    default:
      throw new TypeError('Unknown encoding: ' + encoding);
  }
};


// slice(start, end)
slowProto.slice = function(start, end) {
  var len = this.length;
  start = clamp(start, len, 0);
  end = clamp(end, len, len);
  return new Buffer(this, end - start, start);
};


var zeroBuffer = new SlowBuffer(0);

// Buffer
function Buffer(subject, encoding, offset) {
  if (!(this instanceof Buffer)) {
    return new Buffer(subject, encoding, offset);
  }

  var type;

  // Are we slicing?
  if (typeof offset === 'number') {
    if (!Buffer.isBuffer(subject)) {
      throw new TypeError('First argument must be a Buffer when slicing');
    }

    this.length = +encoding > 0 ? Math.ceil(encoding) : 0;
    this.parent = subject.parent ? subject.parent : subject;
    this.offset = offset;
  } else {
    // Find the length
    switch (type = typeof subject) {
      case 'number':
        this.length = +subject > 0 ? Math.ceil(subject) : 0;
        break;

      case 'string':
        this.length = Buffer.byteLength(subject, encoding);
        break;

      case 'object': // Assume object is array-ish
        this.length = +subject.length > 0 ? Math.ceil(subject.length) : 0;
        break;

      default:
        throw new TypeError('First argument needs to be a number, ' +
                            'array or string.');
    }

    if (this.length > Buffer.poolSize) {
      // Big buffer, just alloc one.
      this.parent = new SlowBuffer(this.length);
      this.offset = 0;

    } else if (this.length > 0) {
      // Small buffer.
      if (!pool || pool.length - pool.used < this.length) allocPool();
      this.parent = pool;
      this.offset = pool.used;
      // Align on 8 byte boundary to avoid alignment issues on ARM.
      pool.used = (pool.used + this.length + 7) & ~7;

    } else {
      // Zero-length buffer
      this.parent = zeroBuffer;
      this.offset = 0;
    }

    // optimize by branching logic for new allocations
    if (typeof subject !== 'number') {
      if (type === 'string') {
        // We are a string
        this.length = this.write(subject, 0, encoding);
      // if subject is buffer then use built-in copy method
      } else if (Buffer.isBuffer(subject)) {
        if (subject.parent)
          subject.parent.copy(this.parent,
                              this.offset,
                              subject.offset,
                              this.length + subject.offset);
        else
          subject.copy(this.parent, this.offset, 0, this.length);
      } else if (isArrayIsh(subject)) {
        for (var i = 0; i < this.length; i++)
          this.parent[i + this.offset] = subject[i];
      }
    }
  }
}

function isArrayIsh(subject) {
  return Array.isArray(subject) ||
         subject && typeof subject === 'object' &&
         typeof subject.length === 'number';
}

exports.SlowBuffer = SlowBuffer;
exports.Buffer = Buffer;


Buffer.isEncoding = function(encoding) {
  switch (encoding && encoding.toLowerCase()) {
    case 'hex':
    case 'utf8':
    case 'utf-8':
    case 'ascii':
    case 'binary':
    case 'base64':
    case 'ucs2':
    case 'ucs-2':
    case 'utf16le':
    case 'utf-16le':
    case 'raw':
      return true;

    default:
      return false;
  }
};



Buffer.poolSize = 8 * 1024;
var pool;

function allocPool() {
  pool = new SlowBuffer(Buffer.poolSize);
  pool.used = 0;
}


// Static methods
Buffer.isBuffer = function isBuffer(b) {
  return b instanceof Buffer;
};


// Inspect
Buffer.prototype.inspect = function inspect() {
  var out = [],
      len = this.length,
      name = this.constructor.name;

  for (var i = 0; i < len; i++) {
    out[i] = toHex(this[i]);
    if (i == exports.INSPECT_MAX_BYTES) {
      out[i + 1] = '...';
      break;
    }
  }

  return '<' + name + ' ' + out.join(' ') + '>';
};
proto.inspect = Buffer.prototype.inspect;


Buffer.prototype.get = function get(offset) {
  if (offset < 0 || offset >= this.length)
    throw new RangeError('offset is out of bounds');
  return this.parent[this.offset + offset];
};
proto.get = Buffer.prototype.get;

Buffer.prototype.set = function set(offset, v) {
  if (offset < 0 || offset >= this.length)
    throw new RangeError('offset is out of bounds');
  return this.parent[this.offset + offset] = v;
};
proto.set = Buffer.prototype.set;

// write(string, offset = 0, length = buffer.length-offset, encoding = 'utf8')
Buffer.prototype.write = function(string, offset, length, encoding) {
  // Support both (string, offset, length, encoding)
  // and the legacy (string, encoding, offset, length)
  if (isFinite(offset)) {
    if (!isFinite(length)) {
      encoding = length;
      length = undefined;
    }
  } else {  // legacy
    var swap = encoding;
    encoding = offset;
    offset = length;
    length = swap;
  }

  offset = +offset || 0;
  var remaining = this.length - offset;
  if (!length) {
    length = remaining;
  } else {
    length = +length;
    if (length > remaining) {
      length = remaining;
    }
  }
  encoding = String(encoding || 'utf8').toLowerCase();

  if (string.length > 0 && (length < 0 || offset < 0))
    throw new RangeError('attempt to write beyond buffer bounds');

  var ret;
  switch (encoding) {
    case 'hex':
      ret = this.parent.hexWrite(string, this.offset + offset, length);
      break;

    case 'utf8':
    case 'utf-8':
      ret = this.parent.utf8Write(string, this.offset + offset, length);
      break;

    case 'ascii':
      ret = this.parent.asciiWrite(string, this.offset + offset, length);
      break;

    case 'binary':
      ret = this.parent.binaryWrite(string, this.offset + offset, length);
      break;

    case 'base64':
      // Warning: maxLength not taken into account in base64Write
      ret = this.parent.base64Write(string, this.offset + offset, length);
      break;

    case 'ucs2':
    case 'ucs-2':
    case 'utf16le':
    case 'utf-16le':
      ret = this.parent.ucs2Write(string, this.offset + offset, length);
      break;

    default:
      throw new TypeError('Unknown encoding: ' + encoding);
  }

  Buffer._charsWritten = SlowBuffer._charsWritten;

  return ret;
};


Buffer.prototype.toJSON = function() {
  return Array.prototype.slice.call(this, 0);
};
proto.toJSON = Buffer.prototype.toJSON;

// toString(encoding, start=0, end=buffer.length)
Buffer.prototype.toString = function(encoding, start, end) {
  encoding = String(encoding || 'utf8').toLowerCase();

  if (typeof start !== 'number' || start < 0) {
    start = 0;
  } else if (start > this.length) {
    start = this.length;
  }

  if (typeof end !== 'number' || end > this.length) {
    end = this.length;
  } else if (end < 0) {
    end = 0;
  }

  start = start + this.offset;
  end = end + this.offset;

  switch (encoding) {
    case 'hex':
      return this.parent.hexSlice(start, end);

    case 'utf8':
    case 'utf-8':
      return this.parent.utf8Slice(start, end);

    case 'ascii':
      return this.parent.asciiSlice(start, end);

    case 'binary':
      return this.parent.binarySlice(start, end);

    case 'base64':
      return this.parent.base64Slice(start, end);

    case 'ucs2':
    case 'ucs-2':
    case 'utf16le':
    case 'utf-16le':
      return this.parent.ucs2Slice(start, end);

    default:
      throw new TypeError('Unknown encoding: ' + encoding);
  }
};


// byteLength
Buffer.byteLength = SlowBuffer.byteLength;


// fill(value, start=0, end=buffer.length)
Buffer.prototype.fill = function fill(value, start, end) {
  value || (value = 0);
  start || (start = 0);
  end || (end = this.length);

  if (typeof value === 'string') {
    value = value.charCodeAt(0);
  }
  if (typeof value !== 'number' || isNaN(value)) {
    throw new TypeError('value is not a number');
  }

  if (end < start) throw new RangeError('end < start');

  // Fill 0 bytes; we're done
  if (end === start) return 0;
  if (this.length == 0) return 0;

  if (start < 0 || start >= this.length) {
    throw new RangeError('start out of bounds');
  }

  if (end < 0 || end > this.length) {
    throw new RangeError('end out of bounds');
  }

  return this.parent.fill(value,
                          start + this.offset,
                          end + this.offset);
};
proto.fill = Buffer.prototype.fill;

Buffer.concat = function(list, length) {
  if (!Array.isArray(list)) {
    throw new TypeError('Usage: Buffer.concat(list, [length])');
  }

  if (list.length === 0) {
    return new Buffer(0);
  } else if (list.length === 1) {
    return list[0];
  }

  if (typeof length !== 'number') {
    length = 0;
    for (var i = 0; i < list.length; i++) {
      var buf = list[i];
      length += buf.length;
    }
  }

  var buffer = new Buffer(length);
  var pos = 0;
  for (var i = 0; i < list.length; i++) {
    var buf = list[i];
    buf.copy(buffer, pos);
    pos += buf.length;
  }
  return buffer;
};




// copy(targetBuffer, targetStart=0, sourceStart=0, sourceEnd=buffer.length)
Buffer.prototype.copy = function(target, target_start, start, end) {
  // set undefined/NaN or out of bounds values equal to their default
  if (!(target_start >= 0)) target_start = 0;
  if (!(start >= 0)) start = 0;
  if (!(end < this.length)) end = this.length;

  // Copy 0 bytes; we're done
  if (end === start ||
      target.length === 0 ||
      this.length === 0 ||
      start > this.length)
    return 0;

  if (end < start)
    throw new RangeError('sourceEnd < sourceStart');

  if (target_start >= target.length)
    throw new RangeError('targetStart out of bounds');

  if (target.length - target_start < end - start)
    end = target.length - target_start + start;

  return this.parent.copy(target.parent || target,
                          target_start + (target.offset || 0),
                          start + this.offset,
                          end + this.offset);
};
proto.copy = Buffer.prototype.copy;

// slice(start, end)
Buffer.prototype.slice = function(start, end) {
  var len = this.length;
  start = clamp(start, len, 0);
  end = clamp(end, len, len);
  return new Buffer(this.parent, end - start, start + this.offset);
};


// Legacy methods for backwards compatibility.

Buffer.prototype.utf8Slice = function(start, end) {
  return this.toString('utf8', start, end);
};
proto.utf8Slice = Buffer.prototype.utf8Slice;

Buffer.prototype.binarySlice = function(start, end) {
  return this.toString('binary', start, end);
};
proto.binarySlice = Buffer.prototype.binarySlice;

Buffer.prototype.asciiSlice = function(start, end) {
  return this.toString('ascii', start, end);
};
proto.asciiSlice = Buffer.prototype.asciiSlice;

Buffer.prototype.utf8Write = function(string, offset) {
  return this.write(string, offset, 'utf8');
};
proto.utf8Write = Buffer.prototype.utf8Write;

Buffer.prototype.binaryWrite = function(string, offset) {
  return this.write(string, offset, 'binary');
};
proto.binaryWrite = Buffer.prototype.binaryWrite;

Buffer.prototype.asciiWrite = function(string, offset) {
  return this.write(string, offset, 'ascii');
};
proto.asciiWrite = Buffer.prototype.asciiWrite;

/*
 * Need to make sure that buffer isn't trying to write out of bounds.
 * This check is far too slow internally for fast buffers.
 */
function checkOffset(offset, ext, length) {
  if ((offset % 1) !== 0 || offset < 0)
    throw new RangeError('offset is not uint');
  if (offset + ext > length)
    throw new RangeError('Trying to access beyond buffer length');
}


Buffer.prototype.readUInt8 = function(offset, noAssert) {
  if (!noAssert)
    checkOffset(offset, 1, this.length);
  return this[offset];
};
proto.readUInt8 = Buffer.prototype.readUInt8;

function readUInt16(buffer, offset, isBigEndian) {
  var val = 0;
  if (isBigEndian) {
    val = buffer[offset] << 8;
    val |= buffer[offset + 1];
  } else {
    val = buffer[offset];
    val |= buffer[offset + 1] << 8;
  }

  return val;
}


Buffer.prototype.readUInt16LE = function(offset, noAssert) {
  if (!noAssert)
    checkOffset(offset, 2, this.length);
  return readUInt16(this, offset, false, noAssert);
};
proto.readUInt16LE = Buffer.prototype.readUInt16LE;

Buffer.prototype.readUInt16BE = function(offset, noAssert) {
  if (!noAssert)
    checkOffset(offset, 2, this.length);
  return readUInt16(this, offset, true, noAssert);
};
proto.readUInt16BE = Buffer.prototype.readUInt16BE;

function readUInt32(buffer, offset, isBigEndian, noAssert) {
  var val = 0;

  if (isBigEndian) {
    val = buffer[offset + 1] << 16;
    val |= buffer[offset + 2] << 8;
    val |= buffer[offset + 3];
    val = val + (buffer[offset] << 24 >>> 0);
  } else {
    val = buffer[offset + 2] << 16;
    val |= buffer[offset + 1] << 8;
    val |= buffer[offset];
    val = val + (buffer[offset + 3] << 24 >>> 0);
  }

  return val;
}


Buffer.prototype.readUInt32LE = function(offset, noAssert) {
  if (!noAssert)
    checkOffset(offset, 4, this.length);
  return readUInt32(this, offset, false, noAssert);
};
proto.redUInt32LE = Buffer.prototype.readUInt32LE;

Buffer.prototype.readUInt32BE = function(offset, noAssert) {
  if (!noAssert)
    checkOffset(offset, 4, this.length);
  return readUInt32(this, offset, true, noAssert);
};
proto.readUInt32BE = Buffer.prototype.readUInt32BE;

/*
 * Signed integer types, yay team! A reminder on how two's complement actually
 * works. The first bit is the signed bit, i.e. tells us whether or not the
 * number should be positive or negative. If the two's complement value is
 * positive, then we're done, as it's equivalent to the unsigned representation.
 *
 * Now if the number is positive, you're pretty much done, you can just leverage
 * the unsigned translations and return those. Unfortunately, negative numbers
 * aren't quite that straightforward.
 *
 * At first glance, one might be inclined to use the traditional formula to
 * translate binary numbers between the positive and negative values in two's
 * complement. (Though it doesn't quite work for the most negative value)
 * Mainly:
 *  - invert all the bits
 *  - add one to the result
 *
 * Of course, this doesn't quite work in Javascript. Take for example the value
 * of -128. This could be represented in 16 bits (big-endian) as 0xff80. But of
 * course, Javascript will do the following:
 *
 * > ~0xff80
 * -65409
 *
 * Whoh there, Javascript, that's not quite right. But wait, according to
 * Javascript that's perfectly correct. When Javascript ends up seeing the
 * constant 0xff80, it has no notion that it is actually a signed number. It
 * assumes that we've input the unsigned value 0xff80. Thus, when it does the
 * binary negation, it casts it into a signed value, (positive 0xff80). Then
 * when you perform binary negation on that, it turns it into a negative number.
 *
 * Instead, we're going to have to use the following general formula, that works
 * in a rather Javascript friendly way. I'm glad we don't support this kind of
 * weird numbering scheme in the kernel.
 *
 * (BIT-MAX - (unsigned)val + 1) * -1
 *
 * The astute observer, may think that this doesn't make sense for 8-bit numbers
 * (really it isn't necessary for them). However, when you get 16-bit numbers,
 * you do. Let's go back to our prior example and see how this will look:
 *
 * (0xffff - 0xff80 + 1) * -1
 * (0x007f + 1) * -1
 * (0x0080) * -1
 */

Buffer.prototype.readInt8 = function(offset, noAssert) {
  if (!noAssert)
    checkOffset(offset, 1, this.length);
  if (!(this[offset] & 0x80))
    return (this[offset]);
  return ((0xff - this[offset] + 1) * -1);
};
proto.readInt8 = Buffer.prototype.readInt8;

function readInt16(buffer, offset, isBigEndian) {
  var val = readUInt16(buffer, offset, isBigEndian);

  if (!(val & 0x8000))
    return val;
  return (0xffff - val + 1) * -1;
}


Buffer.prototype.readInt16LE = function(offset, noAssert) {
  if (!noAssert)
    checkOffset(offset, 2, this.length);
  return readInt16(this, offset, false);
};
proto.redInt16LE = Buffer.prototype.readInt16LE;

Buffer.prototype.readInt16BE = function(offset, noAssert) {
  if (!noAssert)
    checkOffset(offset, 2, this.length);
  return readInt16(this, offset, true);
};
proto.readInt16BE = Buffer.prototype.readInt16BE;

function readInt32(buffer, offset, isBigEndian) {
  var val = readUInt32(buffer, offset, isBigEndian);

  if (!(val & 0x80000000))
    return (val);
  return (0xffffffff - val + 1) * -1;
}


Buffer.prototype.readInt32LE = function(offset, noAssert) {
  if (!noAssert)
    checkOffset(offset, 4, this.length);
  return readInt32(this, offset, false);
};
proto.readInt32LE = Buffer.prototype.readInt32LE;

Buffer.prototype.readInt32BE = function(offset, noAssert) {
  if (!noAssert)
    checkOffset(offset, 4, this.length);
  return readInt32(this, offset, true);
};
proto.readInt32BE = Buffer.prototype.readInt32BE;

Buffer.prototype.readFloatLE = function(offset, noAssert) {
  if (!noAssert)
    checkOffset(offset, 4, this.length);
  return this.parent.readFloatLE(this.offset + offset, !!noAssert);
};
proto.readFloatLE = Buffer.prototype.readFloatLE;

Buffer.prototype.readFloatBE = function(offset, noAssert) {
  if (!noAssert)
    checkOffset(offset, 4, this.length);
  return this.parent.readFloatBE(this.offset + offset, !!noAssert);
};
proto.readFloatBE = Buffer.prototype.readFloatBE;

Buffer.prototype.readDoubleLE = function(offset, noAssert) {
  if (!noAssert)
    checkOffset(offset, 8, this.length);
  return this.parent.readDoubleLE(this.offset + offset, !!noAssert);
};
proto.readDoubleLE = Buffer.prototype.readDoubleLE;

Buffer.prototype.readDoubleBE = function(offset, noAssert) {
  if (!noAssert)
    checkOffset(offset, 8, this.length);
  return this.parent.readDoubleBE(this.offset + offset, !!noAssert);
};
proto.readDoubleBE = Buffer.prototype.readDoubleBE;

function checkInt(buffer, value, offset, ext, max, min) {
  if ((value % 1) !== 0 || value > max || value < min)
    throw TypeError('value is out of bounds');
  if ((offset % 1) !== 0 || offset < 0)
    throw TypeError('offset is not uint');
  if (offset + ext > buffer.length || buffer.length + offset < 0)
    throw RangeError('Trying to write outside buffer length');
}


Buffer.prototype.writeUInt8 = function(value, offset, noAssert) {
  if (!noAssert)
    checkInt(this, value, offset, 1, 0xff, 0);
  this[offset] = value;
};
proto.writeUInt8 = Buffer.prototype.writeUInt8;

function writeUInt16(buffer, value, offset, isBigEndian) {
  if (isBigEndian) {
    buffer[offset] = (value & 0xff00) >>> 8;
    buffer[offset + 1] = value & 0x00ff;
  } else {
    buffer[offset + 1] = (value & 0xff00) >>> 8;
    buffer[offset] = value & 0x00ff;
  }
}


Buffer.prototype.writeUInt16LE = function(value, offset, noAssert) {
  if (!noAssert)
    checkInt(this, value, offset, 2, 0xffff, 0);
  writeUInt16(this, value, offset, false);
};
proto.writeUInt16LE = Buffer.prototype.writeUInt16LE;

Buffer.prototype.writeUInt16BE = function(value, offset, noAssert) {
  if (!noAssert)
    checkInt(this, value, offset, 2, 0xffff, 0);
  writeUInt16(this, value, offset, true);
};
proto.writeUInt16BE = Buffer.prototype.writeUInt16BE;

function writeUInt32(buffer, value, offset, isBigEndian) {
  if (isBigEndian) {
    buffer[offset] = (value >>> 24) & 0xff;
    buffer[offset + 1] = (value >>> 16) & 0xff;
    buffer[offset + 2] = (value >>> 8) & 0xff;
    buffer[offset + 3] = value & 0xff;
  } else {
    buffer[offset + 3] = (value >>> 24) & 0xff;
    buffer[offset + 2] = (value >>> 16) & 0xff;
    buffer[offset + 1] = (value >>> 8) & 0xff;
    buffer[offset] = value & 0xff;
  }
}


Buffer.prototype.writeUInt32LE = function(value, offset, noAssert) {
  if (!noAssert)
    checkInt(this, value, offset, 4, 0xffffffff, 0);
  writeUInt32(this, value, offset, false);
};
proto.writeUInt32LE = Buffer.prototype.writeUInt32LE;

Buffer.prototype.writeUInt32BE = function(value, offset, noAssert) {
  if (!noAssert)
    checkInt(this, value, offset, 4, 0xffffffff, 0);
  writeUInt32(this, value, offset, true);
};
proto.writeUInt32BE = Buffer.prototype.writeUInt32BE;

/*
 * We now move onto our friends in the signed number category. Unlike unsigned
 * numbers, we're going to have to worry a bit more about how we put values into
 * arrays. Since we are only worrying about signed 32-bit values, we're in
 * slightly better shape. Unfortunately, we really can't do our favorite binary
 * & in this system. It really seems to do the wrong thing. For example:
 *
 * > -32 & 0xff
 * 224
 *
 * What's happening above is really: 0xe0 & 0xff = 0xe0. However, the results of
 * this aren't treated as a signed number. Ultimately a bad thing.
 *
 * What we're going to want to do is basically create the unsigned equivalent of
 * our representation and pass that off to the wuint* functions. To do that
 * we're going to do the following:
 *
 *  - if the value is positive
 *      we can pass it directly off to the equivalent wuint
 *  - if the value is negative
 *      we do the following computation:
 *         mb + val + 1, where
 *         mb   is the maximum unsigned value in that byte size
 *         val  is the Javascript negative integer
 *
 *
 * As a concrete value, take -128. In signed 16 bits this would be 0xff80. If
 * you do out the computations:
 *
 * 0xffff - 128 + 1
 * 0xffff - 127
 * 0xff80
 *
 * You can then encode this value as the signed version. This is really rather
 * hacky, but it should work and get the job done which is our goal here.
 */

Buffer.prototype.writeInt8 = function(value, offset, noAssert) {
  if (!noAssert)
    checkInt(this, value, offset, 1, 0x7f, -0x80);
  if (value < 0) value = 0xff + value + 1;
  this[offset] = value;
};
proto.writeInt8 = Buffer.prototype.writeInt8;

Buffer.prototype.writeInt16LE = function(value, offset, noAssert) {
  if (!noAssert)
    checkInt(this, value, offset, 2, 0x7fff, -0x8000);
  if (value < 0) value = 0xffff + value + 1;
  writeUInt16(this, value, offset, false);
};
proto.writeInt16LE = Buffer.prototype.writeInt16LE;

Buffer.prototype.writeInt16BE = function(value, offset, noAssert) {
  if (!noAssert)
    checkInt(this, value, offset, 2, 0x7fff, -0x8000);
  if (value < 0) value = 0xffff + value + 1;
  writeUInt16(this, value, offset, true);
};
proto.writeInt16BE = Buffer.prototype.writeInt16BE;

Buffer.prototype.writeInt32LE = function(value, offset, noAssert) {
  if (!noAssert)
    checkInt(this, value, offset, 4, 0x7fffffff, -0x80000000);
  if (value < 0) value = 0xffffffff + value + 1;
  writeUInt32(this, value, offset, false);
};
proto.writeInt32LE = Buffer.prototype.writeInt32LE;

Buffer.prototype.writeInt32BE = function(value, offset, noAssert) {
  if (!noAssert)
    checkInt(this, value, offset, 4, 0x7fffffff, -0x80000000);
  if (value < 0) value = 0xffffffff + value + 1;
  writeUInt32(this, value, offset, true);
};
proto.writeInt32BE = Buffer.prototype.writeInt32BE;

Buffer.prototype.writeFloatLE = function(value, offset, noAssert) {
  if (!noAssert)
    checkOffset(offset, 4, this.length);
  this.parent.writeFloatLE(value, this.offset + offset, !!noAssert);
};
proto.writeFloatLE = Buffer.prototype.writeFloatLE;

Buffer.prototype.writeFloatBE = function(value, offset, noAssert) {
  if (!noAssert)
    checkOffset(offset, 4, this.length);
  this.parent.writeFloatBE(value, this.offset + offset, !!noAssert);
};
proto.writeFloatBE = Buffer.prototype.writeFloatBE;

Buffer.prototype.writeDoubleLE = function(value, offset, noAssert) {
  if (!noAssert)
    checkOffset(offset, 8, this.length);
  this.parent.writeDoubleLE(value, this.offset + offset, !!noAssert);
};
proto.writeDoubleLE = Buffer.prototype.writeDoubleLE;

Buffer.prototype.writeDoubleBE = function(value, offset, noAssert) {
  if (!noAssert)
    checkOffset(offset, 8, this.length);
  this.parent.writeDoubleBE(value, this.offset + offset, !!noAssert);
};
proto.writeDoubleBE = Buffer.prototype.writeDoubleBE;
