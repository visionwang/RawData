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
package org.apache.myfaces.extensions.cdi.jsf.impl.listener.phase;

import org.apache.myfaces.extensions.cdi.core.api.provider.BeanManagerProvider;
import org.apache.myfaces.extensions.cdi.core.api.scope.conversation.config.WindowContextConfig;
import org.apache.myfaces.extensions.cdi.core.impl.util.ClassDeactivation;
import org.apache.myfaces.extensions.cdi.jsf.impl.listener.request.BeforeAfterFacesRequestBroadcaster;
import org.apache.myfaces.extensions.cdi.jsf.impl.listener.startup.ApplicationStartupBroadcaster;
import org.apache.myfaces.extensions.cdi.jsf.impl.scope.conversation.spi.EditableWindowContextManager;
import org.apache.myfaces.extensions.cdi.jsf.impl.scope.conversation.spi.WindowHandler;
import org.apache.myfaces.extensions.cdi.jsf.impl.scope.conversation.spi.LifecycleAwareWindowHandler;
import org.apache.myfaces.extensions.cdi.core.impl.util.CodiUtils;
import org.apache.myfaces.extensions.cdi.jsf.impl.util.ConversationRequiredUtils;
import org.apache.myfaces.extensions.cdi.jsf.impl.util.ConversationUtils;

import javax.faces.lifecycle.Lifecycle;
import javax.faces.event.PhaseListener;
import javax.faces.context.FacesContext;
import java.util.List;

/**
 * intermediate workaround for
 * {@link org.apache.myfaces.extensions.cdi.jsf.impl.util.JsfUtils#registerPhaseListener}
 */
class CodiLifecycleWrapper extends Lifecycle
{
    private Lifecycle wrapped;

    private BeforeAfterFacesRequestBroadcaster beforeAfterFacesRequestBroadcaster;

    private volatile Boolean initialized;

    private volatile Boolean applicationInitialized;

    CodiLifecycleWrapper(Lifecycle wrapped, List<PhaseListener> phaseListeners)
    {
        this.wrapped = wrapped;

        for (PhaseListener phaseListener : phaseListeners)
        {
            this.wrapped.addPhaseListener(phaseListener);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void addPhaseListener(PhaseListener phaseListener)
    {
        wrapped.addPhaseListener(phaseListener);
    }

    /**
     * Broadcasts {@link org.apache.myfaces.extensions.cdi.core.api.startup.event.StartupEvent} and
     * {@link org.apache.myfaces.extensions.cdi.jsf.api.listener.request.BeforeFacesRequest} btw.
     * {@link org.apache.myfaces.extensions.cdi.jsf.api.listener.request.AfterFacesRequest}
     * <p/>
     * {@inheritDoc}
     */
    public void execute(FacesContext facesContext)
    {
        broadcastApplicationStartupBroadcaster();
        broadcastBeforeFacesRequestEvent(facesContext);

        WindowHandler windowHandler = CodiUtils.getContextualReferenceByClass(WindowHandler.class);

        if (windowHandler instanceof LifecycleAwareWindowHandler)
        {
            ((LifecycleAwareWindowHandler) windowHandler).beforeLifecycleExecute(facesContext);
            if (facesContext.getResponseComplete())
            {
                // no further processing
                return;
            }
        }

        wrapped.execute(facesContext);
    }

    /**
     * {@inheritDoc}
     */
    public PhaseListener[] getPhaseListeners()
    {
        return this.wrapped.getPhaseListeners();
    }

    /**
     * {@inheritDoc}
     */
    public void removePhaseListener(PhaseListener phaseListener)
    {
        wrapped.removePhaseListener(phaseListener);
    }

    /**
     * Performs cleanup tasks after the rendering process
     * <p/>
     * {@inheritDoc}
     */
    public void render(FacesContext facesContext)
    {
        //TODO avoid ContextNotActiveException - details:
        //TODO due to mojarra & weld issues (of some version) we might have to check if the context(s) are active

        ConversationRequiredUtils.ensureExistingConversation(facesContext);

        wrapped.render(facesContext);

        ConversationUtils.postRenderCleanup(facesContext);
    }

    private void broadcastApplicationStartupBroadcaster()
    {
        //just an !additional! check to improve the performance
        if (applicationInitialized == null)
        {
            initApplication();
        }
    }

    private synchronized void initApplication()
    {
        if (applicationInitialized == null)
        {
            applicationInitialized = true;
            ApplicationStartupBroadcaster applicationStartupBroadcaster =
                    CodiUtils.getContextualReferenceByClass(ApplicationStartupBroadcaster.class);
            applicationStartupBroadcaster.broadcastStartupEvent();
        }
    }

    private void broadcastBeforeFacesRequestEvent(FacesContext facesContext)
    {
        lazyInit();
        if (this.beforeAfterFacesRequestBroadcaster != null)
        {
            BeanManagerProvider beanManagerProvider = BeanManagerProvider.getInstance();
            EditableWindowContextManager windowContextManager =
                    beanManagerProvider.getContextualReference(EditableWindowContextManager.class);
            WindowHandler windowHandler =
                    beanManagerProvider.getContextualReference(WindowHandler.class);
            WindowContextConfig windowContextConfig =
                    beanManagerProvider.getContextualReference(WindowContextConfig.class);

            ConversationUtils.tryToRestoreTheWindowIdEagerly(facesContext,
                    windowContextManager, windowHandler, windowContextConfig);

            this.beforeAfterFacesRequestBroadcaster.broadcastBeforeFacesRequestEvent(facesContext);
        }
    }

    private void lazyInit()
    {
        if (this.initialized == null)
        {
            init();
        }
    }

    private synchronized void init()
    {
        // switch into paranoia mode
        if (this.initialized == null)
        {
            if (ClassDeactivation.isClassActivated(BeforeAfterFacesRequestBroadcaster.class))
            {
                this.beforeAfterFacesRequestBroadcaster =
                        CodiUtils.getContextualReferenceByClass(BeforeAfterFacesRequestBroadcaster.class);
            }

            this.initialized = true;
        }
    }
}
