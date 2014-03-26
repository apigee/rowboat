package io.apigee.rowboat.binding.test;

import io.apigee.rowboat.binding.JavaBinder;
import jdk.nashorn.api.scripting.JSObject;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class TestJavaBinding
{
    private static ScriptEngine nashorn;

    @BeforeClass
    public static void init()
    {
        nashorn = new ScriptEngineManager().getEngineByName("nashorn");
    }

    @Test
    public void testBasicBinding()
        throws IOException, ScriptException
    {
        JSObject testObj = JavaBinder.get().bind(DefaultBoundObject.class);
        runScript("/bindingtest.js", testObj);
    }

    @Test
    public void testMethodBinding()
        throws IOException, ScriptException
    {
        JSObject testObj = JavaBinder.get().bind(TestBoundObject.class);
        runScript("/bindingtest.js", testObj);
    }

    @Test
    public void testMethodBindingFunctions()
        throws IOException, ScriptException
    {
        JSObject testObj = JavaBinder.get().bind(TestBoundObject.class);
        runScript("/functiontest.js", testObj);
    }

    private void runScript(String path, JSObject testObj)
        throws IOException, ScriptException
    {
        InputStream in = TestJavaBinding.class.getResourceAsStream(path);
        InputStreamReader rdr = new InputStreamReader(in);

        ScriptContext cx = new SimpleScriptContext();
        cx.setAttribute("test", testObj, ScriptContext.ENGINE_SCOPE);
        nashorn.eval(rdr, cx);
    }
}
