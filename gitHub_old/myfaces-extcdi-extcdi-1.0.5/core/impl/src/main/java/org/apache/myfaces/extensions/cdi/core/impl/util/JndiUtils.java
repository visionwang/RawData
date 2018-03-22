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

import org.apache.myfaces.extensions.cdi.core.api.UnhandledException;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.enterprise.inject.Typed;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This is the internal helper class for low level access to JNDI
 */
@Typed()
public abstract class JndiUtils
{
    private static final Logger LOG = Logger.getLogger(JndiUtils.class.getName());

    private static InitialContext initialContext = null;

    static
    {
        try
        {
            initialContext = new InitialContext();
        }
        catch (Exception e)
        {
            throw new ExceptionInInitializerError(e);
        }
    }

    private JndiUtils()
    {
        // prevent instantiation
    }

    /**
     * Exposes the current {@link InitialContext}
     * @return current initial-context
     */
    public static InitialContext getInitialContext()
    {
        return initialContext;
    }

    /**
     * Binds a given instance to a given name
     * @param name current name
     * @param object current instance
     */
    public static void bind(String name, Object object)
    {
        try
        {
            Context context = initialContext;

            String[] parts = name.split("/");

            for(int i = 0; i < parts.length - 1; i++)
            {
                try
                {
                    context = (Context)initialContext.lookup(parts[i]);
                }
                catch(NameNotFoundException e)
                {
                    context = initialContext.createSubcontext(parts[i]);
                }
            }

            context.bind(parts[parts.length - 1], object);
        }
        catch (NamingException e)
        {
            throw new UnhandledException("Could not bind " + name + " to JNDI", e);
        }
    }

    /**
     * Unbinds a given name
     * @param name current name
     */
    public static void unbind(String name)
    {
        try
        {
            initialContext.unbind(name);

        }
        catch (NamingException e)
        {
            throw new UnhandledException("Could not unbind " + name + " from JNDI", e);
        }
    }

    /**
     * Resolves an instance for the given name.
     * @param name current name
     * @param expectedClass target type
     * @param <T> type
     * @return the found instance, null otherwise
     */
    @SuppressWarnings("unchecked")
    public static <T> T lookup(String name, Class<? extends T> expectedClass)
    {
        try
        {
            Object lookedUp = initialContext.lookup(name);

            if (lookedUp != null)
            {
                if (expectedClass.isAssignableFrom(lookedUp.getClass()))
                {
                    // we have a value and the type fits
                    return (T) lookedUp;
                }
                else if (lookedUp instanceof String)
                {
                    // lookedUp might be a class name
                    try
                    {
                        Class<?>lookedUpClass = Class.forName((String) lookedUp);
                        if (expectedClass.isAssignableFrom(lookedUpClass))
                        {
                            try
                            {
                                return (T) lookedUpClass.newInstance();
                            }
                            catch (Exception e)
                            {
                                // could not create instance
                                LOG.log(Level.SEVERE, "Class " + lookedUpClass + " from JNDI lookup for name "
                                        + name + " could not be instantiated", e);
                            }
                        }
                        else
                        {
                            // lookedUpClass does not extend/implement expectedClass
                            LOG.log(Level.SEVERE, "JNDI lookup for key " + name
                                    + " returned class " + lookedUpClass.getName()
                                    + " which does not implement/extend the expected class"
                                    + expectedClass.getName());
                        }
                    }
                    catch (ClassNotFoundException cnfe)
                    {
                        // could not find class
                        LOG.log(Level.SEVERE, "Could not find Class " + lookedUp
                                + " from JNDI lookup for name " + name, cnfe);
                    }
                }
                else
                {
                    // we have a value, but the value does not fit
                    LOG.log(Level.SEVERE, "JNDI lookup for key " + name + " should return a value of "
                            + expectedClass + ", but returned " + lookedUp);
                }
            }

            return null;
        }
        catch (NamingException e)
        {
            throw new UnhandledException("Could not get " + name + " from JNDI", e);
        }
    }

}
