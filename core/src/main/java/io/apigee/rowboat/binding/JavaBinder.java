/*
 * Copyright 2014 Apigee Corporation.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.apigee.rowboat.binding;

import jdk.nashorn.api.scripting.JSObject;

import java.lang.reflect.Method;

/**
 * This class will wrap an annotated Java object so that it looks like a native JavaScript object.
 */

public class JavaBinder
{
    private static final JavaBinder myself = new JavaBinder();

    private JavaBinder()
    {
    }

    public static JavaBinder get() {
        return myself;
    }

    /**
     * Given the passed object, parse the class and look for methods and fields that are annotated.
     * If they are annotated, turn them into Function properties. The result is a JavaScript function
     * that may be called as a constructor to create instances of the class.
     */
    public JSObject bind(Class<? extends AbstractBoundObject> klass)
    {
        boolean hasConstructor = false;
        ConstructorObject cons = new ConstructorObject(klass);

        for (Method m : klass.getMethods()) {
            if (m.isAnnotationPresent(JSConstructor.class)) {
                if (hasConstructor) {
                    throw new JavaBindingException("Class may only have one @JSConstructor annotation");
                }
                Class<?>[] types = m.getParameterTypes();
                for (Class<?> type : types) {
                    if (!Object.class.equals(type)) {
                        throw new JavaBindingException("@JSConstructor methods must have Object arguments");
                    }
                }
                cons.setConstructor(m, types.length);
                hasConstructor = true;

            } else if (m.isAnnotationPresent(JSFunction.class)) {
                Class<?>[] types = m.getParameterTypes();
                validateMethodArguments(types);
                JSFunction ann = m.getAnnotation(JSFunction.class);
                cons.addMethod(ann.value().equals("") ? m.getName() : ann.value(), m, types.length - 1);

            } else if (m.isAnnotationPresent(JSConstructorFunction.class)) {
                Class<?>[] types = m.getParameterTypes();
                validateMethodArguments(types);
                MethodInfo mi = new MethodInfo(m.getName(), m, types.length - 1);
                JSConstructorFunction ann = m.getAnnotation(JSConstructorFunction.class);
                cons.setMember(ann.value().equals("") ? m.getName() : ann.value(), new FunctionObject(m.getName(), mi));
            }
        }


        return cons;
    }

    private void validateMethodArguments(Class<?>[] types)
    {
        String methodError =
            "@JSFunction methods must have at least one parameter of type Object and a variable number after that";
        if (types.length < 1) {
            throw new JavaBindingException(methodError);
        }
        for (int i = 0; i < types.length; i++) {
            if (!Object.class.equals(types[i])) {
                throw new JavaBindingException(methodError);
            }
        }
    }

    static class MethodInfo
    {
        String name;
        Method method;
        int arity;

        MethodInfo(String name, Method method, int arity)
        {
            this.name = name;
            this.method = method;
            this.arity = arity;
        }
    }
}
