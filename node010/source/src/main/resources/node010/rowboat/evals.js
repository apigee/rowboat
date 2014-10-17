var ScriptContext = Java.type('javax.script.ScriptContext');
var SimpleBindings = Java.type('javax.script.SimpleBindings');

var debugEnabled;
var debug;
if (process.env.NODE_DEBUG && /evals/.test(process.env.NODE_DEBUG)) {
  debugEnabled = true;
  debug = function(x) { console.log(x); }
} else {
  debugEnabled = false;
  debug = function(x) {};
}

function NodeScript() {
}
exports.NodeScript = NodeScript;

function wrapCode(code, fileName) {
  return code + '//# sourceURL=' + fileName;
}

NodeScript.runInThisContext = function(code, fileName) {
  var engine = process.getRuntime().getScriptEngine();
  if (debugEnabled) {
    debug('Evaluating code from ' + fileName + ' in global context');
  }
  // _nashornLoad({ script:  code, name: fileName });
  return engine.eval(wrapCode(code, fileName));
};

NodeScript.runInNewContext = function(code, sandbox, fileName) {
  // Need to use the sandbox, so wrap the code manually
  var engine = process.getRuntime().getScriptEngine();
  // TODO change if we want to use compiled class cache
  if (debugEnabled) {
    debug('Evaluating code from ' + fileName + ' in ' + sandbox);
  }
  return engine.eval(wrapCode(code, fileName), sandbox);
};

function CompiledScript(code, fileName) {
  var engine = process.getRuntime().getScriptEngine();
  this.compiled = engine.compile(wrapCode(code, fileName));
}

NodeScript.compile = function(code, fileName) {
  return new CompiledScript(code, fileName);
};

NodeScript.run = function(context, compiled) {
  if (!(compiled instanceof CompiledScript)) {
    throw new Error('Script was not previously compiled');
  }
  if (context) {
    return compiled.compiled.eval(context);
  }
  return compiled.compiled.eval();
};

NodeScript.createContext = function() {
  return new SimpleBindings();
};

NodeScript.getGlobalContext = function() {
  return process.getRuntime().getScriptEngine().getBindings(ScriptContext.ENGINE_SCOPE);
};
