var util = require('util');

var deprecatedFunc = util.deprecate(function() {
  console.log('Called the function');
}, 'This message should only appear once');
deprecatedFunc();
deprecatedFunc();
deprecatedFunc();

