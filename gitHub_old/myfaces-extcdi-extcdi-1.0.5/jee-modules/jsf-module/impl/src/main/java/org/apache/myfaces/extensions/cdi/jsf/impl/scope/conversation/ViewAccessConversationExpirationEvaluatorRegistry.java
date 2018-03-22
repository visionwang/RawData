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

import org.apache.myfaces.extensions.cdi.core.api.scope.conversation.WindowScoped;
import org.apache.myfaces.extensions.cdi.jsf.impl.scope.conversation.spi.EditableWindowContext;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.faces.context.FacesContext;
import javax.inject.Inject;
import java.io.Serializable;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Helper for the workaround for EXTCDI-49
 */
@WindowScoped
public class ViewAccessConversationExpirationEvaluatorRegistry implements Serializable
{
    private static final long serialVersionUID = -1783266839383634211L;

    @Inject
    private EditableWindowContext windowContext;

    //CopyOnWriteArrayList due to Serializable warning in checkstyle rules
    private CopyOnWriteArrayList<ViewAccessConversationExpirationEvaluator>
            viewAccessConversationExpirationEvaluatorList
            = new CopyOnWriteArrayList<ViewAccessConversationExpirationEvaluator>();

    protected ViewAccessConversationExpirationEvaluatorRegistry()
    {
    }

    void addViewAccessConversationExpirationEvaluator(ViewAccessConversationExpirationEvaluator evaluator)
    {
        this.viewAccessConversationExpirationEvaluatorList.add(evaluator);
    }

    /**
     * Notifies all {@link ViewAccessConversationExpirationEvaluator}s about the rendered view
     * @param viewId current view-id
     */
    public void broadcastRenderedViewId(String viewId)
    {
        for(ViewAccessConversationExpirationEvaluator evaluator : this.viewAccessConversationExpirationEvaluatorList)
        {
            evaluator.observeRenderedView(viewId);

            if(evaluator.isExpired())
            {
                this.viewAccessConversationExpirationEvaluatorList.remove(evaluator);
            }
        }
    }

    @PreDestroy
    protected void save()
    {
        if(FacesContext.getCurrentInstance() == null || !this.windowContext.isActive())
        {
            //here we are outside a request -> currently that's not supported -> TODO
            return;
        }
        this.windowContext.setAttribute(ViewAccessConversationExpirationEvaluatorRegistry.class.getName(),
                this.viewAccessConversationExpirationEvaluatorList, true);
    }

    @PostConstruct
    protected void restore()
    {
        if(FacesContext.getCurrentInstance() == null || !this.windowContext.isActive())
        {
            //here we are outside a request -> currently that's not supported -> TODO
            return;
        }
        CopyOnWriteArrayList<ViewAccessConversationExpirationEvaluator> restoredList =
            this.windowContext.getAttribute(ViewAccessConversationExpirationEvaluatorRegistry.class.getName(),
                    CopyOnWriteArrayList.class);

        if(restoredList != null)
        {
            this.viewAccessConversationExpirationEvaluatorList = restoredList;
        }
    }
}
