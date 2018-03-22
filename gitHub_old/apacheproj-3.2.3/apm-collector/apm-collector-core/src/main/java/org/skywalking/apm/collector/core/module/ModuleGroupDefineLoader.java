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

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.skywalking.apm.collector.core.framework.Loader;
import org.skywalking.apm.collector.core.util.DefinitionLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ModuleGroupDefineLoader implements Loader<Map<String, ModuleGroupDefine>> {

    private final Logger logger = LoggerFactory.getLogger(ModuleGroupDefineLoader.class);

    @Override public Map<String, ModuleGroupDefine> load() throws DefineException {
        Map<String, ModuleGroupDefine> moduleGroupDefineMap = new LinkedHashMap<>();

        ModuleGroupDefineFile definitionFile = new ModuleGroupDefineFile();
        logger.info("module group definition file name: {}", definitionFile.fileName());
        DefinitionLoader<ModuleGroupDefine> definitionLoader = DefinitionLoader.load(ModuleGroupDefine.class, definitionFile);
        Iterator<ModuleGroupDefine> defineIterator = definitionLoader.iterator();
        while (defineIterator.hasNext()) {
            ModuleGroupDefine groupDefine = defineIterator.next();
            String groupName = groupDefine.name().toLowerCase();
            moduleGroupDefineMap.put(groupName, groupDefine);
        }
        return moduleGroupDefineMap;
    }
}
