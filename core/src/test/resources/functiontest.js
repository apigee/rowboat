function assert(x) {
  if (!x) {
    throw new Error('Assertion Error: x = ' + x);
  }
}

var o = new test('foo', 'bar');

assert(o.getOne() === 'foo');
assert(o.getTwo() === 'bar');

o.setOne('this');
print('getOne() = ' + o.getOne());
assert(o.getOne() === 'this');

o.setTwo(123);
assert(o.getTwo() === 123);


