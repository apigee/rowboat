package io.apigee.rowboat.test;

import io.apigee.rowboat.node40.Node40Implementation;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

import javax.script.Compilable;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

public class CompileTest
{
    private static Compilable engine;
    private static Node40Implementation imp;

    @BeforeClass
    public static void init()
    {
        engine = (Compilable)(new ScriptEngineManager().getEngineByName("nashorn"));
        imp = new Node40Implementation();
    }

    @Test
    public void testCompileBuiltins()
        throws IOException, ScriptException
    {
        for (String[] m : imp.getBuiltInModules()) {
            assert(m.length == 2);
            Reader r = readResource(m[1]);
            try {
                engine.compile(r);
            } finally {
                r.close();
            }
        }
    }

    @Test
    public void testCompileInternal()
        throws IOException, ScriptException
    {
        for (String[] m : imp.getInternalModules()) {
            assert(m.length == 2);
            Reader r = readResource(m[1]);
            try {
                engine.compile(r);
            } finally {
                r.close();
            }
        }
    }

    private Reader readResource(String name)
        throws IOException
    {
        InputStream in =
            imp.getClass().getResourceAsStream(name);
        assertNotNull(name + " is not found", in);
        return new InputStreamReader(in);
    }
}
