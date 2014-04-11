package io.apigee.rowboat.binding.test;

import io.apigee.rowboat.binding.AbstractScriptObject;
import io.apigee.rowboat.binding.JSConstructor;
import io.apigee.rowboat.binding.JSFunction;

public class TestBoundObject
    extends AbstractScriptObject
{
    private Object foo;
    private Object bar;

    @Override
    public String getClassName() {
        return "TestBoundObject";
    }

    @JSConstructor
    public void init(Object foo, Object bar)
    {
        this.foo = foo;
        this.bar = bar;
    }

    @JSFunction
    public void setOne(Object thiz, Object bar)
    {
        this.foo = bar;
    }

    @JSFunction
    public Object getOne(Object thiz)
    {
        return foo;
    }

    @JSFunction
    public void setTwo(Object thiz, Object bar)
    {
        this.bar = bar;
    }

    @JSFunction
    public Object getTwo(Object thiz)
    {
        return bar;
    }
}
