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
package io.micronaut.data.neo4j.operations;

import io.micronaut.data.graph.runtime.operations.GraphRepositoryOperations;
import io.micronaut.data.operations.reactive.ReactiveCapableRepository;

/**
 * Repository operations for Neo4j.
 *
 * @author graemerocher
 * @since 5.3.0
 */
public interface Neo4jRepositoryOperations extends GraphRepositoryOperations, ReactiveCapableRepository {

    /**
     * @return The reactive operations, which run on the driver's reactive sessions
     */
    @Override
    Neo4jReactorRepositoryOperations reactive();
}
