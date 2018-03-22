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

package org.apache.myfaces.extensions.scripting.cdi.api;


import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * <p>This class provides access to the ContainerControl.</p>
 * <p>It uses the {@code java.util.ServiceLoader} mechanism  to 
 * automatically pickup the container providers from the classpath.</p>
 */
public final class CdiContainerLoader
{
    private CdiContainerLoader()
    {
        // private ct to prevent instantiation
    }

    
    public static CdiContainer getCdiContainer()
    {
        CdiContainer testContainer;

        //doesn't support the implementation loader (there is no dependency to owb-impl
        ServiceLoader<CdiContainer> cdiContainerLoader = ServiceLoader.load(CdiContainer.class);
        Iterator<CdiContainer> cdiIt = cdiContainerLoader.iterator();
        if (cdiIt.hasNext())
        {
            testContainer = cdiIt.next();
        }
        else
        {
            throw new IllegalStateException("Could not find an implementation of " + CdiContainer.class.getName() +
                " available in the classpath!");
        }

        if (cdiIt.hasNext())
        {
            String foundContainers = getContainerDetails();
            throw new IllegalStateException("Too many implementations of " + CdiContainer.class.getName() +
                " found in the classpath! Details: " + foundContainers);
        }

        return testContainer;
    }

    private static String getContainerDetails()
    {
        StringBuilder result = new StringBuilder();

        Class containerClass;
        for (CdiContainer cdiContainer : ServiceLoader.load(CdiContainer.class))
        {
            containerClass = cdiContainer.getClass();
            result.append(containerClass.getProtectionDomain().getCodeSource().getLocation().toExternalForm());
            result.append(containerClass.getName());

            result.append(System.getProperty("line.separator"));
        }

        return result.toString();
    }
}
