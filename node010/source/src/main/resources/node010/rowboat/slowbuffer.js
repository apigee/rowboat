var JavaSlowBuffer = Java.type('io.apigee.rowboat.node010.classes.SlowBuffer');
var SlowBuffer = process.binding('buffer').SlowBuffer;

SlowBuffer._charsWritten = 0;
SlowBuffer.byteLength = 0;

var proto = {};

function constructSlowBuffer(b) {
  for (var n in proto) {
    b[n] = proto[n];
  }
}

// This wires up the SlowBuffer java class so that it will always add our prototype functions in its constructor.
JavaSlowBuffer.setInternalConstructor(SlowBuffer, constructSlowBuffer);

proto.fill = function(value, start, end) {
  throw new Error('Not implemented');
};

proto.copy = function(target, targetStart, start, end) {
  throw new Error('Not implemented');
};

// These return strings
proto.hexSlice = function(start, length) {
  throw new Error('Not implemented');
};

proto.utf8Slice = function(start, length) {
  throw new Error('Not implemented');
};

proto.asciiSlice = function(start, length) {
  throw new Error('Not implemented');
};

proto.binarySlice = function(start, length) {
  throw new Error('Not implemented');
};

proto.base64Slice = function(start, length) {
  throw new Error('Not implemented');
};

proto.ucs2Slice = function(start, length) {
  throw new Error('Not implemented');
};

// These decode the strings and copy the bytes
proto.hexWrite = function(str, offset, length) {
  throw new Error('Not implemented');
};

proto.utf8Write = function(str, offset, length) {
  throw new Error('Not implemented');
};

proto.asciiWrite = function(str, offset, length) {
  throw new Error('Not implemented');
};

proto.binaryWrite = function(str, offset, length) {
  throw new Error('Not implemented');
};

proto.base64Write = function(str, offset, length) {
  throw new Error('Not implemented');
};

proto.ucs2Write = function(str, offset, length) {
  throw new Error('Not implemented');
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
