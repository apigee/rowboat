var assert = require('assert');
var name = 'testmodule';

exports.modulename = name;
exports.modulefunc = function() {
  return 'testmodule.modulefunc';
};
exports.geterrno = function() {
  return errno;
};
exports.seterrno = function(e) {
  errno = e;
};
assert.equal(process.argv.length, 2);
