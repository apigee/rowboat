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
package io.apigee.rowboat.modules;

import io.apigee.rowboat.InternalNodeModule;
import io.apigee.rowboat.NodeRuntime;
import io.apigee.rowboat.binding.DefaultScriptObject;
import jdk.nashorn.api.scripting.JSObject;

import java.util.HashMap;

/**
 * Includes all the constants from the built-in "constants" module in Node.
 */
public class Constants
    implements InternalNodeModule
{
    public static final int O_APPEND    = 0x0008;
    public static final int O_CREAT     = 0x0200;
    public static final int O_DIRECTORY = 0x100000;
    public static final int O_EXCL      = 0x0800;
    public static final int O_NOCTTY    = 0x20000;
    public static final int O_NOFOLLOW  = 0x0100;
    public static final int O_RDONLY    = 0x0000;
    public static final int O_RDWR      = 0x0002;
    // If this variable is present, "lchmod" is supported. It doesn't seem to fully work
    // in Java 7 so we are disabling it.
    // public static final int O_SYMLINK   = 0x200000;
    public static final int O_SYNC      = 0x0080;
    public static final int O_TRUNC     = 0x0400;
    public static final int O_WRONLY = 0x0001;

    public static final int S_IFDIR = 0040000;
    public static final int S_IFREG = 0100000;
    public static final int S_IFBLK = 0060000;
    public static final int S_IFCHR = 0020000;
    public static final int S_IFLNK = 0120000;
    public static final int S_IFIFO = 0010000;
    public static final int S_IFSOCK = 0140000;
    public static final int S_IFMT =  0170000;

    public static final String EACCES = "EACCES";
    public static final String EADDRINUSE = "EADDRINUSE";
    public static final String EBADF = "EBADF";
    public static final String ECONNREFUSED = "ECONNREFUSED";
    public static final String EINTR = "EINTR";
    public static final String EEXIST = "EEXIST";
    public static final String EINVAL = "EINVAL";
    public static final String EIO = "EIO";
    public static final String EILSEQ = "EILSEQ";
    public static final String EISDIR = "EISDIR";
    public static final String ENOTFOUND = "ENOTFOUND";
    public static final String ENOENT = "ENOENT";
    public static final String ENOTDIR = "ENOTDIR";
    public static final String ENOTEMPTY = "ENOTEMPTY";
    public static final String EOF = "EOF";
    public static final String EPERM = "EPERM";
    public static final String EPIPE = "EPIPE";
    public static final String ESRCH = "ESRCH";

    public static final String SIGHUP = "SIGHUP";
    public static final String SIGINT = "SIGINT";
    public static final String SIGKILL = "SIGKILL";
    public static final String SIGQUIT = "SIGQUIT";
    public static final String SIGTERM = "SIGTERM";

    public static final int S_IRUSR = 0000400;    /* R for owner */
    public static final int S_IWUSR = 0000200;    /* W for owner */
    public static final int S_IXUSR = 0000100;    /* X for owner */
    public static final int S_IRGRP = 0000040;    /* R for group */
    public static final int S_IWGRP = 0000020;    /* W for group */
    public static final int S_IXGRP = 0000010;    /* X for group */
    public static final int S_IROTH = 0000004;    /* R for other */
    public static final int S_IWOTH = 0000002;    /* W for other */
    public static final int S_IXOTH = 0000001;    /* X for other */

    private static final HashMap<String, Integer> errnos = new HashMap<String, Integer>();

    static {
        errnos.put(EACCES, 13);
        errnos.put(EADDRINUSE, 48);
        errnos.put(EBADF, 9);
        errnos.put(ECONNREFUSED, 61);
        errnos.put(EINTR, 4);
        errnos.put(EEXIST, 17);
        errnos.put(EINVAL, 22);
        errnos.put(EIO, 5);
        errnos.put(EILSEQ, 92);
        errnos.put(EISDIR, 21);
        // TODO this isn't quite right -- not defined on my Mac at least
        errnos.put(ENOTFOUND, 2);
        errnos.put(ENOTEMPTY, 66);
        errnos.put(ENOENT, 2);
        errnos.put(ENOTDIR, 20);
        errnos.put(EPERM, 1);
        errnos.put(EPIPE, 32);
        errnos.put(ESRCH, 3);
    }


    @Override
    public String getModuleName()
    {
        return "constants";
    }

    /**
     * Register integer constants that are required by lots of node code -- mainly OS-level stuff.
     */
    @Override
    public Object getExports(NodeRuntime runner)
    {
        JSObject exports = new DefaultScriptObject();

        exports.setMember("O_APPEND", O_APPEND);
        exports.setMember("O_CREAT", O_CREAT);
        exports.setMember("O_DIRECTORY", O_DIRECTORY);
        exports.setMember("O_EXCL", O_EXCL);
        exports.setMember("O_NOCTTY", O_NOCTTY);
        exports.setMember("O_NOFOLLOW", O_NOFOLLOW);
        exports.setMember("O_RDONLY", O_RDONLY);
        exports.setMember("O_RDWR", O_RDWR);
        // See above regarding "lchmod"
        //exports.setMember("O_SYMLINK", O_SYMLINK);
        exports.setMember("O_SYNC", O_SYNC);
        exports.setMember("O_TRUNC", O_TRUNC);
        exports.setMember("O_WRONLY", O_WRONLY);

        exports.setMember("S_IFDIR", S_IFDIR);
        exports.setMember("S_IFREG", S_IFREG);
        exports.setMember("S_IFBLK", S_IFBLK);
        exports.setMember("S_IFCHR", S_IFCHR);
        exports.setMember("S_IFLNK", S_IFLNK);
        exports.setMember("S_IFIFO", S_IFIFO);
        exports.setMember("S_IFSOCK", S_IFSOCK);
        exports.setMember("S_IFMT", S_IFMT);

        exports.setMember("SIGHUP", SIGHUP);
        exports.setMember("SIGINT", SIGINT);
        exports.setMember("SIGKILL", SIGKILL);
        exports.setMember("SIGTERM", SIGTERM);
        exports.setMember("SIGQUIT", SIGQUIT);

        return exports;
    }

    /**
     * Given an error code string, return the numerical error code that would have been returned on
     * a standard Unix system, or -1 if the specified error code isn't found or isn't an error code
     */
    public static int getErrno(String code)
    {
        Integer errno = errnos.get(code);
        if (errno == null) {
            return -1;
        }
        return errno;
    }
}
