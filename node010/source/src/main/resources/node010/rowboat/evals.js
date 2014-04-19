var ScriptContext = Java.type('javax.script.ScriptContext');
var SimpleBindings = Java.type('javax.script.SimpleBindings');

function NodeScript() {
}
exports.NodeScript = NodeScript;

NodeScript.runInThisContext = function(code, fileName) {
  var engine = process.getRuntime().getScriptEngine();
  // TODO stash the file name somewhere so that we get stack traces
  return engine.eval(code);
};

NodeScript.runInNewContext = function(code, sandbox, fileName) {
  var engine = process.getRuntime().getScriptEngine();
  engine.eval(code, sandbox);
};

function CompiledScript(code, fileName) {
  var engine = process.getRuntime().getScriptEngine();
  this.compiled = engine.compile(code);
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
