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

package org.skywalking.apm.collector.core.graph;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @author wu-sheng
 */
public class GraphNodeFinder {
    private Graph graph;

    GraphNodeFinder(Graph graph) {
        this.graph = graph;
    }

    /**
     * Find an exist node to build the graph.
     *
     * @param handlerId of specific node in graph.
     * @param outputClass of the found node
     * @param <NODEOUTPUT> type of given output class
     * @return Node instance.
     */
    public <NODEOUTPUT> Node<?, NODEOUTPUT> findNode(int handlerId, Class<NODEOUTPUT> outputClass) {
        ConcurrentHashMap<Integer, Node> graphNodeIndex = graph.getNodeIndex();
        Node node = graphNodeIndex.get(handlerId);
        if (node == null) {
            throw new NodeNotFoundException("Can't find node with handlerId="
                + handlerId
                + " in graph[" + graph.getId() + "]");
        }
        return node;
    }

    public Next findNext(int handlerId) {
        ConcurrentHashMap<Integer, Node> graphNodeIndex = graph.getNodeIndex();
        Node node = graphNodeIndex.get(handlerId);
        if (node == null) {
            throw new NodeNotFoundException("Can't find node with handlerId="
                + handlerId
                + " in graph[" + graph.getId() + "]");
        }
        return node.getNext();
    }
}
