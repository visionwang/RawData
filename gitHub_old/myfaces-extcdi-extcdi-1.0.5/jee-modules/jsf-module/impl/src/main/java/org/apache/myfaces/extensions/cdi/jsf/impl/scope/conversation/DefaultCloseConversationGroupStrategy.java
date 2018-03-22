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
package org.apache.myfaces.extensions.cdi.jsf.impl.scope.conversation;

import org.apache.myfaces.extensions.cdi.jsf.impl.scope.conversation.spi.CloseConversationGroupStrategy;
import org.apache.myfaces.extensions.cdi.core.api.scope.conversation.WindowContext;
import org.apache.myfaces.extensions.cdi.core.api.scope.conversation.CloseConversationGroup;

import javax.interceptor.InvocationContext;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import java.lang.reflect.Method;

/**
 * {@inheritDoc}
 */
@Dependent
public class DefaultCloseConversationGroupStrategy implements CloseConversationGroupStrategy
{
    private static final long serialVersionUID = 3781434188201702757L;

    @Inject
    private WindowContext windowContext;

    /**
     * {@inheritDoc}
     */
    public Object execute(InvocationContext invocationContext) throws Exception
    {
        Object result = null;

        CloseConversationGroup closeConversationGroup = getCloseConversationGroupAnnotation(invocationContext);

        RuntimeException catchedException = null;

        try
        {
            result = invocationContext.proceed();
        }
        catch (RuntimeException exception)
        {
            catchedException = exception;
        }

        if(isDefaultExceptionValue(closeConversationGroup) ||
                (catchedException != null && checkExceptionToCatch(catchedException, closeConversationGroup.on())))
        {
            Class conversationGroup = getConversationGroup(invocationContext, closeConversationGroup);

            this.windowContext.closeConversation(conversationGroup);
        }
        return result;
    }

    private Class getConversationGroup(InvocationContext invocationContext,
                                       CloseConversationGroup closeConversationGroup)
    {
        Class conversationGroup = closeConversationGroup.group();

        if(CloseConversationGroup.class.isAssignableFrom(conversationGroup))
        {
            conversationGroup = invocationContext.getMethod().getDeclaringClass();
            //TODO support more use-cases
        }
        return conversationGroup;
    }

    private boolean checkExceptionToCatch(RuntimeException catchedException,
                                          Class<? extends RuntimeException> specifiedException)
    {
        if(specifiedException.isAssignableFrom(catchedException.getClass()))
        {
            return true;
        }
        throw catchedException;
    }

    private CloseConversationGroup getCloseConversationGroupAnnotation(InvocationContext invocationContext)
    {
        Method method = invocationContext.getMethod();
        return method.getAnnotation(CloseConversationGroup.class);
    }

    private boolean isDefaultExceptionValue(CloseConversationGroup closeConversationGroup)
    {
        return RuntimeException.class.getName().equals(closeConversationGroup.on().getName());
    }
}
