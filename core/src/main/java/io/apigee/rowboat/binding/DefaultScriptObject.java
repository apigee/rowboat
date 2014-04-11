package io.apigee.rowboat.binding;

public class DefaultScriptObject
    extends AbstractScriptObject
{
    private final String className;

    public DefaultScriptObject()
    {
        className = null;
    }

    public DefaultScriptObject(String className)
    {
        this.className = className;
    }

    @Override
    public String getClassName()
    {
        return className;
    }
}
