var Charsets = Java.type('io.apigee.rowboat.internal.Charsets');
var JavaSlowBuffer = Java.type('io.apigee.rowboat.node010.classes.SlowBuffer');

var SlowBuffer = process.binding('buffer').SlowBuffer;

SlowBuffer._charsWritten = 0;

SlowBuffer.byteLength = function(str, encoding) {
  return JavaSlowBuffer.calculateByteLength(str, String(encoding));
};

var proto = {};

function constructSlowBuffer(b, handle) {
  for (var n in proto) {
    b[n] = proto[n];
  }
  b._handle = handle;
}

// This wires up the SlowBuffer java class so that it will always add our prototype functions in its constructor.
JavaSlowBuffer.setInternalConstructor(SlowBuffer, constructSlowBuffer);

// Convert the buffer to a ByteBuffer that represents only its own content.
// Returns an object that can only be passed to Java code
proto.toJava = function() {
  return JavaSlowBuffer.convertBuffer(this, 0, this.length);
};

proto.fill = function(value, start, end) {
  this._handle.fill(value, start, end);
};

proto.copy = function(target, targetStart, start, end) {
  return this._handle.copy(target._handle, targetStart, start, end);
};

// These return strings
proto.hexSlice = function(start, end) {
  return this._handle.slice(start, end, Charsets.NODE_HEX);
};

proto.utf8Slice = function(start, end) {
  return this._handle.slice(start, end, Charsets.UTF8);
};

proto.asciiSlice = function(start, end) {
  return this._handle.slice(start, end, Charsets.ASCII);
};

proto.binarySlice = function(start, end) {
  return this._handle.slice(start, end, Charsets.NODE_BINARY);
};

proto.base64Slice = function(start, end) {
  return this._handle.slice(start, end, Charsets.BASE64);
};

proto.ucs2Slice = function(start, end) {
  return this._handle.slice(start, end, Charsets.UCS2);
};

function updateCharsWritten(c) {
  SlowBuffer._charsWritten = c;
}

// These decode the strings and copy the bytes
proto.hexWrite = function(str, offset, length) {
  return this._handle.write(str, offset, length, Charsets.NODE_HEX, updateCharsWritten);
};

proto.utf8Write = function(str, offset, length) {
  return this._handle.write(str, offset, length, Charsets.UTF8, updateCharsWritten);
};

proto.asciiWrite = function(str, offset, length) {
  return this._handle.write(str, offset, length, Charsets.ASCII, updateCharsWritten);
};

proto.binaryWrite = function(str, offset, length) {
  return this._handle.write(str, offset, length, Charsets.NODE_BINARY, updateCharsWritten);
};

proto.base64Write = function(str, offset, length) {
  return this._handle.write(str, offset, length, Charsets.BASE64, updateCharsWritten);
};

proto.ucs2Write = function(str, offset, length) {
  return this._handle.write(str, offset, length, Charsets.UCS2, updateCharsWritten);
};

proto.readFloatLE = function(offset, noassert) {
  throw new Error('Not implemented');
};

proto.readFloatBE = function(offset, noassert) {
  throw new Error('Not implemented');
};

proto.readDoubleLE = function(offset, noassert) {
  throw new Error('Not implemented');
};

proto.readDoubleBE = function(offset, noassert) {
  throw new Error('Not implemented');
};

proto.writeFloatLE = function(value, offset, noassert) {
  throw new Error('Not implemented');
};

proto.writeFloatBE = function(value, offset, noassert) {
  throw new Error('Not implemented');
};

proto.writeDoubleLE = function(value, offset, noassert) {
  throw new Error('Not implemented');
};

proto.writeDoubleBE = function(value, offset, noassert) {
  throw new Error('Not implemented');
};
