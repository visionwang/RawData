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

import org.apache.myfaces.extensions.cdi.core.api.activation.ProjectStageActivated;
import org.apache.myfaces.extensions.cdi.core.api.config.CodiCoreConfig;
import org.apache.myfaces.extensions.cdi.core.api.projectstage.ProjectStage;
import org.apache.myfaces.extensions.cdi.core.api.startup.CodiStartupBroadcaster;
import org.apache.myfaces.extensions.cdi.core.api.util.ClassUtils;
import org.apache.myfaces.extensions.cdi.core.api.tools.InvocationOrderComparator;
import org.apache.myfaces.extensions.cdi.core.impl.projectstage.ProjectStageProducer;
import org.apache.myfaces.extensions.cdi.core.impl.util.CodiUtils;
import org.apache.myfaces.extensions.cdi.jsf.api.listener.phase.JsfPhaseListener;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.faces.event.PhaseListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.apache.myfaces.extensions.cdi.core.impl.util.ClassDeactivation.isClassActivated;
import static org.apache.myfaces.extensions.cdi.core.impl.util.CodiUtils.injectFields;

/**
 * The PhaseListenerExtension picks up all {@link JsfPhaseListener} annotated
 * beans for later registration as PhaseListeners.
 * We have to maintain this separately for each ContextClassLoader since it
 * is possible that multiple WebApps start up in parallel.
 */
public class PhaseListenerExtension implements Extension
{
    private static Map<ClassLoader, List<Class<? extends PhaseListener>>> phaseListeners = 
            new ConcurrentHashMap<ClassLoader,List<Class<? extends PhaseListener>>>();

    /**
     * Filters beans annotated with {@link JsfPhaseListener}.
     * The class will be stored for consuming it later (see #consumePhaseListeners)
     * @param processAnnotatedType current process-annotated-type
     */
    public void filterJsfPhaseListeners(@Observes ProcessAnnotatedType processAnnotatedType)
    {
        CodiStartupBroadcaster.broadcastStartup();

        if (processAnnotatedType.getAnnotatedType().isAnnotationPresent(JsfPhaseListener.class))
        {
            Class<? extends PhaseListener> phaseListenerClass
                    = processAnnotatedType.getAnnotatedType().getJavaClass();

            if(isClassActivated(phaseListenerClass))
            {
                addPhaseListener(phaseListenerClass);
            }

            processAnnotatedType.veto();
        }
    }

    private void addPhaseListener(Class<? extends PhaseListener> newPhaseListener)
    {
        ClassLoader classLoader = getClassLoader();

        List<Class<? extends PhaseListener>> phaseListenerClass = phaseListeners.get(classLoader);

        if (phaseListenerClass == null)
        {
            phaseListenerClass = new CopyOnWriteArrayList<Class<? extends PhaseListener>>();
            phaseListeners.put(classLoader, phaseListenerClass);
        }

        // just add the Class of the PhaseListener and do not instantiate it now,
        // because there is no FacesContext available at this point and the
        // constructor of the PhaseListener could use it (possible in JSF 2.0)
        phaseListenerClass.add(newPhaseListener);
    }

    /**
     * Exposes the found phase-listeners for the invocation. Afterwards it will return an empty list.
     * @return found phase-listeners for the first invocation,
     * an empty list if there are no phase-listeners or if they are consumed already.
     */
    public static List<PhaseListener> consumePhaseListeners()
    {
        //workaround for mojarra
        CodiStartupBroadcaster.broadcastStartup();

        ClassLoader classLoader = getClassLoader();
        List<Class<? extends PhaseListener>> foundPhaseListeners = phaseListeners.get(classLoader);

        boolean advancedQualifierRequiredForDependencyInjection =
                CodiUtils.getContextualReferenceByClass(CodiCoreConfig.class)
                        .isAdvancedQualifierRequiredForDependencyInjection();

        if(foundPhaseListeners != null && ! foundPhaseListeners.isEmpty())
        {
            List<PhaseListener> result = new ArrayList<PhaseListener>(foundPhaseListeners.size());

            Class<? extends ProjectStage> activeProjectStage =
                    ProjectStageProducer.getInstance().getProjectStage().getClass();

            for(Class<? extends PhaseListener> phaseListenerClass : foundPhaseListeners)
            {
                if(phaseListenerClass.isAnnotationPresent(ProjectStageActivated.class))
                {
                    boolean projectStageFound = false;

                    for(Class<? extends ProjectStage> currentProjectStage :
                            phaseListenerClass.getAnnotation(ProjectStageActivated.class).value())
                    {
                        if(currentProjectStage.isAssignableFrom(activeProjectStage))
                        {
                            projectStageFound = true;
                        }
                    }

                    if(!projectStageFound)
                    {
                        continue;
                    }
                }

                PhaseListener phaseListener = createPhaseListenerInstance(phaseListenerClass);
                result.add(injectFields(phaseListener, advancedQualifierRequiredForDependencyInjection));
            }

            foundPhaseListeners.clear();

            Collections.sort(result, new InvocationOrderComparator<PhaseListener>());
            return result;
        }
        return Collections.emptyList();
    }

    private static PhaseListener createPhaseListenerInstance(Class<? extends PhaseListener> phaseListenerClass)
    {
        return ClassUtils.tryToInstantiateClass(phaseListenerClass);
    }

    private static ClassLoader getClassLoader()
    {
        return ClassUtils.getClassLoader(null);
    }
}
