package io.apigee.rowboat.node010.modules;

import io.apigee.rowboat.InternalNodeModule;
import io.apigee.rowboat.NodeRuntime;
import io.apigee.rowboat.binding.DefaultScriptObject;
import io.apigee.rowboat.node010.classes.SlowBuffer;

public class InternalBufferModule
    implements InternalNodeModule
{
    @Override
    public String getModuleName()
    {
        return "buffer";
    }

    @Override
    public Object getExports(NodeRuntime runtime)
    {
        DefaultScriptObject ret = new DefaultScriptObject();
        ret.setMember("SlowBuffer", new SlowBuffer());
        return ret;
    }
}
