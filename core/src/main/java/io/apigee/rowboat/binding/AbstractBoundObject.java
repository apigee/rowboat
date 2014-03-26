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

import jdk.nashorn.api.scripting.AbstractJSObject;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Java classes that are wrapped by JavaBinder must implement this class.
 */

public abstract class AbstractBoundObject
    extends AbstractJSObject
{
    private Map<String, Object> members = Collections.emptyMap();

    @Override
    public abstract String getClassName();

    @Override
    public Object getMember(String name)
    {
        Object m = members.get(name);
        if (m instanceof Property) {

    }

    @Override
    public boolean hasMember(String name)
    {
        return members.containsKey(name);
    }

    @Override
    public void removeMember(String name)
    {
        members.remove(name);
    }

    @Override
    public void setMember(String name, Object value)
    {
        if (members.isEmpty()) {
            members = new HashMap<String, Object>();
        }
        members.put(name, value);
    }

    @Override
    public Object getSlot(int index)
    {
        return getMember(String.valueOf(index));
    }

    @Override
    public boolean hasSlot(int slot)
    {
        return hasMember(String.valueOf(slot));
    }

    @Override
    public void setSlot(int index, Object value)
    {
        setMember(String.valueOf(index), value);
    }

    @Override
    public Set<String> keySet()
    {
        return members.keySet();
    }

    @Override
    public Collection<Object> values()
    {
        return members.values();
    }

    @Override
    public boolean isInstance(Object instance)
    {
        return (getClass().isInstance(instance));
    }

    @Override
    public boolean isInstanceOf(Object clazz)
    {
        try {
            return (getClass().isAssignableFrom((Class)clazz));
        } catch (ClassCastException cce) {
            return false;
        }
    }
}
