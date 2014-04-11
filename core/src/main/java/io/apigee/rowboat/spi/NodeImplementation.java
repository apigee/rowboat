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

package io.apigee.rowboat.spi;

import io.apigee.rowboat.NodeModule;

import java.util.Collection;

/**
 * This class represents an implementation of Node.js -- it contains the JavaScript necessary to run all the
 * various modules.
 */
public interface NodeImplementation
{
    /**
     * Return the version of Node.js supported here. It must be a three-digit string like "0.10.24".
     */
    String getVersion();

    /**
     * Return the resource name of the class that contains the
     * compiled JavaScript for the "main" of the implementation. It is usually derived
     * from "node.js". The name must be a string that can be passed to "getResource" to return a resource
     * in this implementation's JAR file.
     */
    String getMainScript();

    /**
     * Return a two-dimensional array of built-in module names. The first element must be the name
     * of the module, like "http," and the second must be the name of a compiled script resource
     * that implements the module. The script resource will be compiled as JavaScript.
     * The name must be a string that can be passed to "getResource" to return a resource
     * in this implementation's JAR file.
     */
    String[][] getBuiltInModules();

    /**
     * Return the same array, but for modules loaded using "process.binding" instead of "require".
     */
    String[][] getInternalModules();

    /**
     * Return an array of modules that represent Java code to be loaded directly in the runtime.
     * These modules will be loaded via "require".
     */
    Collection<Class<? extends NodeModule>> getJavaModules();

    /**
     * Return an array of modules that represent Java code to be loaded directly in the runtime.
     * These modules will be loaded via "process.binding".
     */
    Collection<Class<? extends NodeModule>> getInternalJavaModules();
}
