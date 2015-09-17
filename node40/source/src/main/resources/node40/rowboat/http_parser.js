var ByteBuffer =         Java.type('java.nio.ByteBuffer');
var HTTPParsingMachine = Java.type('io.apigee.trireme.kernel.http.HTTPParsingMachine');

var REQUEST = 1;
var RESPONSE = 2;

var EMPTY_BUF = ByteBuffer.allocate(0);

function HTTPParser(parserType) {
  if (!(this instanceof HTTPParser)) {
    return new HTTPParser(parserType);
  }

  // Make this value non-enumerable
  Object.defineProperty(this, 'parser', {
    value: undefined,
    writable: true
  });

  this.init(parserType);
  this.sentPartialHeaders = false;
  this.sentCompleteHeaders = false;
}
module.exports.HTTPParser = HTTPParser;

HTTPParser.REQUEST = REQUEST;
HTTPParser.RESPONSE = RESPONSE;

HTTPParser.prototype.init = function(parserType) {
  // Make non-enumerable
  switch (parserType) {
    case REQUEST:
      this.parser = new HTTPParsingMachine(HTTPParsingMachine.ParsingMode.REQUEST);
      break;
    case RESPONSE:
      this.parser = new HTTPParsingMachine(HTTPParsingMachine.ParsingMode.RESPONSE);
      break;
    default:
      throw new Error('Invalid HTTP parsing type ' + parserType);
  }
};

HTTPParser.prototype.reinitialize = function(parserType) {
  this.init(parserType);
};

HTTPParser.prototype.pause = function() {
  // Nothing to pause in our implementation
};

HTTPParser.prototype.resume = function() {
};

HTTPParser.prototype.finish = function() {
  return execute(this, EMPTY_BUF);
};

HTTPParser.prototype.execute = function(buf, offset, length) {
  var bBuf = buf.toJava();
  bBuf.position(bBuf.position() + offset);
  bBuf.limit(bBuf.position() + length);

  return execute(this, bBuf);
};

function execute(self, buf) {
  var hadSomething;
  var wasComplete;
  var result;
  var startPos = buf.position();

  do {
    hadSomething = false;
    wasComplete = false;
    result = self.parser.parse(buf);

    if (result.isError()) {
      var e = new Error('Parsing error');
      e.code = 'HPE_INVALID_CONSTANT';
      throw e;
    }

    if (!self.sentCompleteHeaders) {
      if (result.isHeadersComplete() || result.isComplete()) {
        self.sentCompleteHeaders = true;
        hadSomething = true;
        if (result.hasHeaders() && self.sentPartialHeaders) {
          callOnHeaders(self, result);
        }
        if (callOnHeadersComplete(self, result)) {
          // The JS code returns this when the request was a HEAD
          self.parser.setIgnoreBody(true);
        }
      } else if (result.hasHeaders()) {
        hadSomething = true;
        self.sentPartialHeaders = true;
        callOnHeaders(self, result);
      }
    }
    if (result.hasTrailers()) {
      hadSomething = true;
      callOnTrailers(self, result);
    }
    if (result.hasBody()) {
      hadSomething = true;
      callOnBody(self, result);
    }
    if (result.isComplete()) {
      hadSomething = true;
      wasComplete = true;
      callOnComplete(self);

      // Reset so that the loop starts where we picked up
      self.sentPartialHeaders = false;
      self.sentCompleteHeaders = false;
      self.parser.reset();
    }
  } while (hadSomething && buf.hasRemaining() && !result.isConnectRequest());
  return buf.position() - startPos;
}

function callOnHeadersComplete(self, result) {
  if (self.onHeadersComplete) {
    var info = {
      url: result.getUri(),
      versionMajor: result.getMajor(),
      versionMinor: result.getMinor(),
      method: result.getMethod(),
      statusCode: result.getStatusCode(),
      shouldKeepAlive: result.shouldKeepAlive()
    };
    info.upgrade =
      (result.isUpgradeRequested() || (info.method === 'connect') || (info.method === 'CONNECT'));

    if (!self.sentPartialHeaders) {
      info.headers = buildMap(result.getHeaders());
    }

    return self.onHeadersComplete(info);
  }
  return false;
}

function callOnHeaders(self, result) {
  if (self.onHeaders) {
    self.onHeaders(buildMap(result.getHeaders()), result.getUri());
  }
}

function callOnTrailers(self, result) {
  if (self.onHeaders) {
    self.onHeaders(buildMap(result.getTrailers()), result.getUri());
  }
}

function callOnBody(self, result) {
  if (self.onBody) {
    var buf = Buffer.fromJava(result.getBody());
    self.onBody(buf, 0, buf.length);
  }
}

function callOnComplete(self) {
  if (self.onMessageComplete) {
    self.onMessageComplete();
  }
}

// Take a List of Map.Entry objects and turn them into an array with key, value, key, value, etc.
function buildMap(m) {
  var r = [];
  m.forEach(function(e) {
    r.push(e.getKey());
    r.push(e.getValue());
  });
  return r;
}

