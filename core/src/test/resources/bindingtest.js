function assert(x) {
  if (!x) {
    throw new Error('Assertion Error: x = ' + x);
  }
}

var o = new test();
print('We created ' + o);
o.foo = 'Foo';
o.bar = 123;
o[0] = 456;
o[1] = 789;

assert(o.foo === 'Foo');
assert(o.bar === 123);
assert(o[0] === 456);
assert(o[1] === 789);

var num = 0;
for (n in o) {
  print('o[' + n + '] = ' + o[n]);
  num++;
}



