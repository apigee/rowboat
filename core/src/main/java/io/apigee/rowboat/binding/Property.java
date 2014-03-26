package io.apigee.rowboat.binding;

import java.lang.reflect.Method;

public class Property
{
    private Object target;
    private Class<?> type;
    private Method getter;
    private Method setter;

    void setTarget(Object target) {
        this.target = target;
    }

    Object getTarget() {
        return target;
    }

    void setGetter(Method getter) {
        this.getter = getter;
    }

    void setSetter(Method setter) {
        this.setter = setter;
    }

    Method getGetter() {
        return getter;
    }

    Method getSetter() {
        return setter;
    }
}
