/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.graph.runtime;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * A node read from a graph database, independent of the driver.
 *
 * @param id         The identifier the database assigned to the node
 * @param labels     The labels
 * @param properties The properties
 * @param numericId  The numeric identifier of the node, if the database has one besides {@code id}
 * @author graemerocher
 * @since 5.3.0
 */
public record GraphNode(Object id, List<String> labels, Map<String, Object> properties, @Nullable Long numericId) {

    /**
     * A node whose identifier is the only one the database has.
     *
     * @param id         The identifier the database assigned to the node
     * @param labels     The labels
     * @param properties The properties
     */
    public GraphNode(Object id, List<String> labels, Map<String, Object> properties) {
        this(id, labels, properties, null);
    }
}
