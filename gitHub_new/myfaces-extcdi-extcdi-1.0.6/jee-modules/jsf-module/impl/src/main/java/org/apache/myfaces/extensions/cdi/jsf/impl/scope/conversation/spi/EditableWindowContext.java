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
package org.apache.myfaces.extensions.cdi.jsf.impl.scope.conversation.spi;

import org.apache.myfaces.extensions.cdi.core.api.scope.conversation.WindowContext;

import java.util.Map;
import java.util.Date;
import java.lang.annotation.Annotation;

/**
 * Allows to implement custom implementations of {@link WindowContext}
 */
public interface EditableWindowContext extends WindowContext
{
    /**
     * @return evaluates and returns if the context is active
     */
    boolean isActive();

    /**
     * Date of the last access of the whole {@link WindowContext}
     * @return date of the last access
     */
    Date getLastAccess();

    /**
     * Signals that the {@link WindowContext} gets used
     */
    void touch();

    /**
     * Removes conversations which are already marked as expired
     */
    void removeInactiveConversations();

    /**
     * TODO
     * @return all active conversations of the current context
     */
    Map<ConversationKey /*conversation group*/, EditableConversation> getConversations();

    /**
     * @param conversationGroup group of the conversation in question
     * @param qualifiers optional qualifiers for the conversation
     * @return a new conversation for the given group
     */
    EditableConversation createConversation(Class<?> conversationGroup, Annotation... qualifiers);

    /**
     * @param conversationGroup group of the conversation in question
     * @param qualifiers optional qualifiers for the conversation
     * @return a new conversation for the given group
     */
    EditableConversation getConversation(Class<?> conversationGroup, Annotation... qualifiers);

    /**
     * @param conversationGroup group of the conversation in question
     * @param qualifiers optional qualifiers for the conversation
     * @return true if the conversation exists and is active, false otherwise
     */
    boolean isConversationActive(Class<?> conversationGroup, Annotation... qualifiers);
}
