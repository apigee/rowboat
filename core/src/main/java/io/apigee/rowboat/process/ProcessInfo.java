package io.apigee.rowboat.process;

import io.apigee.trireme.kernel.ErrorCodes;
import io.apigee.trireme.kernel.OSException;
import io.apigee.trireme.kernel.handles.AbstractHandle;

import java.util.HashMap;
import java.util.Map;

public class ProcessInfo
{
    enum StdioType { PIPE, IGNORE, FD, IPC }

    private String[] args;
    private String cwd;
    private Map<String, String> environment;
    private final HashMap<Integer, StdioType> stdioTypes = new HashMap<>();
    private final HashMap<Integer, Integer> stdioFds = new HashMap<>();
    private final HashMap<Integer, AbstractHandle> stdioHandles = new HashMap<>();

    public String[] getArgs()
    {
        return args;
    }

    public void setArgs(String[] args)
    {
        this.args = args;
    }

    public String getCwd()
    {
        return cwd;
    }

    public void setCwd(String cwd)
    {
        this.cwd = cwd;
    }

    public Map<String, String> getEnvironment()
    {
        return environment;
    }

    public void setEnvironment(Map<String, String> environment)
    {
        this.environment = environment;
    }

    public void setStdio(int i, String tn, int fd)
        throws OSException
    {
        StdioType type;
        switch (tn) {
        case "pipe":
            type = StdioType.PIPE;
            break;
        case "ipc":
            type = StdioType.IPC;
            break;
        case "fd":
            type = StdioType.FD;
            break;
        case "ignore":
            type = StdioType.IGNORE;
            break;
        default:
            throw new OSException(ErrorCodes.EINVAL, "Invalid stdio type " + tn);
        }

        stdioTypes.put(i, type);
        stdioFds.put(i, fd);
    }

    public StdioType getStdioType(int i)
    {
        return stdioTypes.get(i);
    }

    public int getStdioFd(int i)
    {
        return stdioFds.get(i);
    }

    public void setStdioHandle(int i, AbstractHandle handle)
    {
        stdioHandles.put(i, handle);
    }

    public AbstractHandle getStdioHandle(int i)
    {
        return stdioHandles.get(i);
    }
}
