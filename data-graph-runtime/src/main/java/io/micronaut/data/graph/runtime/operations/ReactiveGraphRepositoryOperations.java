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
package io.micronaut.data.graph.runtime.operations;

import io.micronaut.data.graph.runtime.GraphRecord;
import io.micronaut.data.operations.reactive.ReactorReactiveRepositoryOperations;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * Reactive repository operations of a graph database, with access to queries written in the database's query language.
 *
 * @author graemerocher
 * @since 5.3.0
 */
public interface ReactiveGraphRepositoryOperations extends ReactorReactiveRepositoryOperations {

    /**
     * Runs a query in the database's query language and streams its rows.
     *
     * @param query      The query, with parameters written as {@code $name}
     * @param parameters The parameters
     * @return The rows
     */
    Flux<GraphRecord> executeQuery(String query, Map<String, Object> parameters);

    /**
     * Runs a query and maps the first column of each row to the given type. Nodes map to the entity the type names, or
     * to the entity whose label matches when the type is {@code Object}.
     *
     * @param query      The query, with parameters written as {@code $name}
     * @param parameters The parameters
     * @param type       The type of each result
     * @param <T>        The type of each result
     * @return The results
     */
    <T> Flux<T> executeQuery(String query, Map<String, Object> parameters, Class<T> type);
}
