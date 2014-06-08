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

if (!process.versions.openssl) {
  console.error('Skipping because node compiled without OpenSSL.');
  process.exit(0);
}

var common = require('../common');
var assert = require('assert');
var fs = require('fs');
var tls = require('tls');
var path = require('path');

var cert = fs.readFileSync(path.join(common.fixturesDir, 'test_cert.pem'));
var key = fs.readFileSync(path.join(common.fixturesDir, 'test_key.pem'));

var errorEmitted = false;

var server = tls.createServer({
  cert: cert,
  key: key
}, onConnect);
server.listen(common.PORT, function() {
  var conn = tls.connect({
    cert: cert,
    key: key,
    rejectUnauthorized: false,
    port: common.PORT
  }, function() {
    setTimeout(function() {
      conn.destroy();
    }, 20);
  });

  // SSL_write() call's return value, when called 0 bytes, should not be
  // treated as error.
  conn.end('');

  conn.on('error', function(err) {
    console.log(err);
    errorEmitted = true;
  });
});

function onConnect(c) {
  // Nop
  setTimeout(function() {
    c.destroy();
    server.close();
  }, 20);
}


process.on('exit', function() {
  assert.ok(!errorEmitted);
});
