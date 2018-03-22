/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.batchee.extras.typed;

import javax.batch.api.chunk.ItemWriter;
import java.io.Serializable;
import java.util.List;

public abstract class TypedItemWriter<R, C extends Serializable> implements ItemWriter {
    protected abstract void doOpen(C checkpoint);
    protected abstract C doCheckpointInfo();
    protected abstract void doWriteItems(List<R> items);

    @Override
    public void open(final Serializable checkpoint) throws Exception {
        doOpen((C) checkpoint);
    }

    @Override
    public void writeItems(final List<Object> items) throws Exception {
        doWriteItems((List<R>) items);
    }

    @Override
    public Serializable checkpointInfo() throws Exception {
        return doCheckpointInfo();
    }
}
