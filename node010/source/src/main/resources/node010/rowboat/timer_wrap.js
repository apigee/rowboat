var Referenceable = process.binding('referenceable').Referenceable;
var util = require('util');

function Timer() {
  Referenceable.call(this);
  this.ref();
}
exports.Timer = Timer;
util.inherits(Timer, Referenceable);

Timer.prototype.start = function(timeout, interval) {
  throw new Error('Timers not yet implemented');
};

Timer.prototype.close = function() {
  this.unref();
};

