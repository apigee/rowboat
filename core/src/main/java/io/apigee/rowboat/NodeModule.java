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
package io.apigee.rowboat;

/**
 * <p>This is a superclass for all modules that may be loaded natively in Java. All modules with this interface
 * listed in META-INF/services will be loaded automatically and required when necessary, and these may
 * also be part of a NodeImplementation.</p>
 * <p>Objects returned by this module must be objects that can be directly called by JavaScript -- typically
 * they implement the "JSObject" interface. The "JavaBinder" may be used to generate these more
 * easily.
 * </p>
 */
public interface NodeModule
{
    /**
     * Return the top-level name of the module as it'd be looked up in a "require" call.
     */
    String getModuleName();

    /**
     * <p>Return a JavaScript object that is the equivalent of the "exports" object in a Node module
     * that was written in JavaScript. Typically this will be a "JSObject" (in Nashorn). The JavaBinder
     * class may be used to create such an object from an annotated Java class.
     * </p>
     *
     * @param runtime an object that may be used to interact with the script runtime
     * @return the "exports" for the specified module, which may be empty but must not be null
     */
    Object getExports(NodeRuntime runtime);
}
