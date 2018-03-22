/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.core.module;

import java.util.Properties;

/**
 * @author wu-sheng
 */
public class ModuleAProvider extends ModuleProvider {
    @Override public String name() {
        return "P-A";
    }

    @Override public Class<? extends Module> module() {
        return BaseModuleA.class;
    }

    @Override public void prepare(Properties config) throws ServiceNotProvidedException {
        this.registerServiceImplementation(BaseModuleA.ServiceABusiness1.class, new ModuleABusiness1Impl());
        this.registerServiceImplementation(BaseModuleA.ServiceABusiness2.class, new ModuleABusiness2Impl());
    }

    @Override public void start(Properties config) throws ServiceNotProvidedException {
    }

    @Override public void notifyAfterCompleted() throws ServiceNotProvidedException {

    }

    @Override public String[] requiredModules() {
        return new String[0];
    }
}
