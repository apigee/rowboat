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
var IPAddressUtil = Java.type('sun.net.util.IPAddressUtil');
var DNSWrap = Java.type('io.apigee.rowboat.internal.DNSWrap');

var Wrapper = new DNSWrap(process.getRuntime());

exports.isIP = function(addr) {
  if (!addr || (addr === '') || (addr === '0')) {
    return 0;
  }
  if (IPAddressUtil.isIPv4LiteralAddress(addr)) {
    return 4;
  }
  if (IPAddressUtil.isIPv6LiteralAddress(addr)) {
    return 6;
  }
};

// Do an asynchronous lookup and call the callback with the result.
exports.getaddrinfo = function(domain, family) {
  var req = {};
  var self = this;
  Wrapper.getAllByName(domain, family, function(err, result) {
    lookupComplete(self, req, err, result);
  });
  return req;
};

function lookupComplete(self, req, err, result) {
  // This ALWAYS returns in the next tick so we will call the callback immediately here
  if (req.oncomplete) {
    if (err) {
      process._errno = err;
      req.oncomplete.call(self);
    } else {
      var addresses = Java.from(result);
      req.oncomplete.call(self, addresses);
    }
  }
}

// TODO: export a function for each type of lookup
// We may want to use the "dns.js" module from NPM (isaacs) for this.
