package io.apigee.rowboat.node010.classes;

import io.apigee.rowboat.binding.AbstractScriptObject;
import io.apigee.rowboat.binding.JSConstructor;

import java.util.function.Consumer;

public class SlowBuffer
    extends AbstractScriptObject
{
    public static final String CLASS_NAME = "SlowBuffer";

    private Consumer<SlowBuffer> constructor;
    private Consumer<SlowBuffer> internalConstructor;
    private byte[] buffer;

    @Override
    public String getClassName() {
        return CLASS_NAME;
    }

    public static void setConstructor(SlowBuffer target, Consumer<SlowBuffer> func)
    {
        target.constructor = func;
    }

    public static void setInternalConstructor(SlowBuffer target, Consumer<SlowBuffer> func)
    {
        target.internalConstructor = func;
    }

    public SlowBuffer()
    {
    }

    public SlowBuffer(int length)
    {
        buffer = new byte[length];
    }

    @Override
    public Object newObject(Object... args)
    {
        int length = ((Number)args[0]).intValue();
        SlowBuffer ret = new SlowBuffer(length);
        if (internalConstructor != null) {
            internalConstructor.accept(ret);
        }
        if (constructor != null) {
            constructor.accept(ret);
        }
        return ret;
    }

    @Override
    public Object getSlot(int index)
    {
        if ((index >= 0) && (index < buffer.length)) {
            return buffer[index] & 0xff;
        }
        return null;
    }

    @Override
    public void setSlot(int i, Object v)
    {
        if ((i >= 0) && (i < buffer.length)) {
            int val = ((Number)v).intValue();
            if (val < 0) {
                val = 0xff + val + 1;
            }
            buffer[i] = (byte)(val & 0xff);
        }
    }

    @Override
    public boolean hasSlot(int i)
    {
        return ((i >= 0) && (i < buffer.length));
    }

    @Override
    public boolean isInstance(Object o)
    {
        return (o instanceof SlowBuffer);
    }
}

