/*
 * Copyright (c) 2014-2016, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided
 * with the distribution.
 * * Neither the name of The Linux Foundation nor the names of its
 * contributors may be used to endorse or promote products derived
 * from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package org.codeaurora.swe;

import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Field;

public class ReflectHelper {

    private final static String LOGTAG = "ReflectHelper";

    public static Object newObject(String className, Class[] argTypes, Object[] args) {
        Object obj = null;

        try{
            Class clazz = Class.forName(className);

            if (args == null || args.length == 0) {
                obj = clazz.newInstance();
            } else {
                Constructor ctor = clazz.getDeclaredConstructor(argTypes);
                obj = ctor.newInstance(args);
            }
        }
        catch (Exception e) {
            Log.e(LOGTAG, "An exception occured : " + e.getMessage() );
        }
        return obj;
    }

    public static Object invokeMethod(Object obj, String method, Class[] argTypes, Object[] args) {
        boolean modifiedAccessibility = false;
        Object result = null;
        Method m = null;

        if (obj == null || method == null)
            return null;

        try {
            if (obj instanceof String){
                //Process call as a static method call
                String className = (String)obj;
                obj = null;
                Class clazz = Class.forName(className);
                m = clazz.getDeclaredMethod(method, argTypes);
            } else {
                //Process call on instance of obj
                m = obj.getClass().getDeclaredMethod(method, argTypes);
            }

            if(m != null) {
                if (!m.isAccessible()) {
                    modifiedAccessibility = true;
                    m.setAccessible(true);
                }
                result = m.invoke(obj, args);
                if (modifiedAccessibility)
                    m.setAccessible(false);
            }
        } catch (Exception e) {
            Log.e(LOGTAG, "An exception occured : " + e.getMessage() );
            return null;
        }
        return result;
    }

    public static Object invokeProxyMethod(String proxyClassName, String method, Object obj,
                                           Class[] proxyArgTypes, Object[] args) {
        Object result = null;
        boolean modifiedAccessibility = false;
        if (proxyClassName == null || method == null) {
            throw new IllegalArgumentException("Object and Method must be supplied.");
        }
        try {
            Class clazz = Class.forName(proxyClassName);
            Method m = clazz.getDeclaredMethod(method, proxyArgTypes);
            if(m != null) {
                // make it visible
                if (!m.isAccessible()) {
                    modifiedAccessibility = true;
                    m.setAccessible(true);
                }
                result = m.invoke(obj, args);
                if (modifiedAccessibility)
                    m.setAccessible(false);
                }
            } catch (Exception e) {
                Log.e(LOGTAG, "An exception occured : " + e.getMessage() );
            }
        return result;
    }

    public static Object getStaticVariable(String className, String fieldName) {
        Object result  = null;
        boolean modifiedAccessibility = false;
        try {
            Class clazz = Class.forName(className);
            Field f = clazz.getDeclaredField(fieldName);
            if(f != null) {
                if (!f.isAccessible()) {
                    modifiedAccessibility = true;
                    f.setAccessible(true);
                }
                f.setAccessible(true);
                result = f.get(null);
                if (modifiedAccessibility)
                    f.setAccessible(false);
            }
        } catch (Exception e) {
            Log.e(LOGTAG, "An exception occured : " + e.getMessage() );
        }
        return result;
    }

    public static Object getVariable(Object obj, String fieldName) {
        Object result  = null;
        boolean modifiedAccessibility = false;
        try {
            Class clazz = obj.getClass();
            Field f = clazz.getDeclaredField(fieldName);
            if(f != null) {
                if (!f.isAccessible()) {
                    modifiedAccessibility = true;
                    f.setAccessible(true);
                }
                f.setAccessible(true);
                result = f.get(obj);
                if (modifiedAccessibility)
                    f.setAccessible(false);
            }
        } catch (Exception e) {
            Log.e(LOGTAG, "An exception occured : " + e.getMessage() );
        }
        return result;
    }
}