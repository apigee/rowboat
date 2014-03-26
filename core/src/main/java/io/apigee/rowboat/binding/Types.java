package io.apigee.rowboat.binding;

import jdk.nashorn.api.scripting.JSObject;

import javax.script.ScriptException;

public class Types
{
    private interface Conv
    {
        Object toJs(Object t);
        Object fromJs(Object o);
    }

    private static final class ConvObject
        implements Conv
    {
        @Override
        public Object toJs(Object t)
        {
            return t;
        }

        @Override
        public Object fromJs(Object o)
        {
            return o;
        }
    }

    private static final class ConvString
        implements Conv
    {
        @Override
        public Object toJs(Object t)
        {
            return t.toString();
        }

        @Override
        public Object fromJs(Object o)
        {
            return o.toString();
        }
    }

    private static final class ConvJSObject
        implements Conv
    {
        @Override
        public Object toJs(Object t)
        {
            try {
                return (JSObject)t;
            } catch (ClassCastException cce) {
                throw new RuntimeException("Returned value is not a JavaScript object");
            }
        }

        @Override
        public Object fromJs(Object o)
        {
            return toJs(o);
        }
    }

    private static final class ConvInt
        implements Conv
    {
        @Override
        public Object toJs(Object t)
        {
            return Integer.valueOf((Number)t);
        }

        @Override
        public Object fromJs(Object o)
        {
            return o.toString();
        }
    }
}
