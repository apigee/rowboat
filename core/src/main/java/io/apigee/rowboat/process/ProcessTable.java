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
package io.apigee.rowboat.process;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The process table is a JVM-wide singleton, regardless of NodeEnvironment. That lets us share processes
 * across Trireme threads in the same JVM when spawning. Also, the concept of a "pid" is hidden in Java so we
 * need to make it up...
 */

public class ProcessTable
{
    private static final ProcessTable myself = new ProcessTable();

    private final AtomicInteger nextPid = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, Object> processTable =
        new ConcurrentHashMap<>();

    private ProcessTable()
    {
    }

    public static ProcessTable get() {
        return myself;
    }

    public int getNextPid() {
        return nextPid.getAndIncrement();
    }

    public void add(int pid, Object process)
    {
        processTable.put(pid, process);
    }

    public void remove(int pid)
    {
        processTable.remove(pid);
    }
}
