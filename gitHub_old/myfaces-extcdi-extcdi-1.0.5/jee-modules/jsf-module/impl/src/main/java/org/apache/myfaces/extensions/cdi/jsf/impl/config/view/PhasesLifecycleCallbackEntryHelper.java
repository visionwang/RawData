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
package org.apache.myfaces.extensions.cdi.jsf.impl.config.view;

import org.apache.myfaces.extensions.cdi.jsf.api.listener.phase.JsfPhaseId;
import static org.apache.myfaces.extensions.cdi.jsf.api.listener.phase.JsfPhaseId.convertToFacesClass;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * Helper for {@link DefaultPageBeanDescriptor}
 */
class PhasesLifecycleCallbackEntryHelper
{
    private Map<javax.faces.event.PhaseId, List<Method>> methods
            = new HashMap<javax.faces.event.PhaseId, List<Method>>();

    void add(JsfPhaseId phaseId, Method method)
    {
        javax.faces.event.PhaseId id = convertToFacesClass(phaseId);
        List<Method> methodList = this.methods.get(id);

        if(methodList == null)
        {
            methodList = new ArrayList<Method>();
            methods.put(id, methodList);
        }
        methodList.add(method);
    }

    boolean isEmpty()
    {
        return methods.isEmpty();
    }

    List<Method> getMethodsFor(javax.faces.event.PhaseId phaseId)
    {
        return methods.get(phaseId);
    }
}
