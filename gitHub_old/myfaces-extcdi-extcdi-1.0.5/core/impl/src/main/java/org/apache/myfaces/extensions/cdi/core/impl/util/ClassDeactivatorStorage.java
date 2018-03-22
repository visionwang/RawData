/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.myfaces.extensions.cdi.core.impl.util;

import org.apache.myfaces.extensions.cdi.core.api.activation.ClassDeactivator;
import org.apache.myfaces.extensions.cdi.core.api.util.ClassUtils;

import javax.enterprise.inject.Typed;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache for {@link ClassDeactivator} implementations
 */
@Typed()
class ClassDeactivatorStorage
{
    private static Map<ClassLoader, ClassDeactivator> classDeactivatorMap
            = new ConcurrentHashMap<ClassLoader, ClassDeactivator>();

    private ClassDeactivatorStorage()
    {
        // prevent instantiation
    }

    static void setClassDeactivator(ClassDeactivator classDeactivator)
    {
        if(classDeactivator != null)
        {
            classDeactivatorMap.put(getClassLoader(), classDeactivator);
        }
        else
        {
            classDeactivatorMap.remove(getClassLoader());
        }
    }

    static ClassDeactivator getClassDeactivator()
    {
        if(!classDeactivatorMap.containsKey(getClassLoader()))
        {
            return null;
        }
        return classDeactivatorMap.get(getClassLoader());
    }

    private static ClassLoader getClassLoader()
    {
        return ClassUtils.getClassLoader(null);
    }
}
