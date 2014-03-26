package io.apigee.rowboat.binding;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

public class ConstructorObject
    extends AbstractBoundObject
{
    private final Class<? extends AbstractBoundObject> klass;
    private final ArrayList<JavaBinder.MethodInfo> methods = new ArrayList<>();
    private JavaBinder.MethodInfo constructor;

    ConstructorObject(Class<? extends AbstractBoundObject> klass)
    {
        this.klass = klass;
    }

    void setConstructor(Method m, int arity)
    {
        this.constructor = new JavaBinder.MethodInfo("_constructor", m, arity);
    }

    void addMethod(String name, Method m, int arity)
    {
        methods.add(new JavaBinder.MethodInfo(name, m, arity));
    }

    @Override
    public String getClassName() {
        return "_constructor";
    }

    @Override
    public boolean isFunction() {
        return true;
    }

    @Override
    public Object call(Object thiz, Object... args)
    {
        return newObject(args);
    }

    @Override
    public Object newObject(Object... args)
    {
        try {
            AbstractBoundObject newObj = klass.newInstance();
            if (constructor != null) {
                invokeConstructor(newObj, args);
            }

            for (JavaBinder.MethodInfo mi : methods) {
                newObj.setMember(mi.name, new FunctionObject(newObj, mi));
            }

            return newObj;
        } catch (InstantiationException|IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private void invokeConstructor(AbstractBoundObject newObj, Object... args)
    {
        Object[] targetArgs = new Object[constructor.arity];
        for (int i = 0; i < constructor.arity; i++) {
            if (i < args.length) {
                targetArgs[i] = args[i];
            } else {
                targetArgs[i] = null;
            }
        }
        try {
            constructor.method.invoke(newObj, targetArgs);
        } catch (IllegalAccessException|InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
