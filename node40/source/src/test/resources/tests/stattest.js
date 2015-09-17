var assert = require('assert');
var fs = require('fs');

var shouldBe = './target/test-classes/tests/stattest.js';
var shouldNot = '/does/not/exist';

var s1 = fs.statSync(shouldBe);
console.log('stat(%s) = %j', shouldBe, s1);
assert(s1);

try {
  fs.statSync(shouldNot);
  assert(false);
} catch (e) {
  console.log('stat(%s) = %j', shouldNot, e);
}

