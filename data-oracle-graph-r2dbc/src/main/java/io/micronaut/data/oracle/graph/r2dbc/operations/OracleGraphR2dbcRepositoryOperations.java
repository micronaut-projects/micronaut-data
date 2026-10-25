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
package io.micronaut.data.oracle.graph.r2dbc.operations;

import io.micronaut.data.graph.runtime.operations.GraphRepositoryOperations;
import io.micronaut.data.operations.reactive.ReactiveCapableRepository;

/**
 * Repository operations for Oracle property graphs on an R2DBC connection factory. The operations are reactive:
 * {@link #reactive()} runs them, and the blocking operations are not supported.
 *
 * @author graemerocher
 * @since 5.3.0
 */
public interface OracleGraphR2dbcRepositoryOperations extends GraphRepositoryOperations, ReactiveCapableRepository {

    @Override
    OracleGraphReactorRepositoryOperations reactive();
}
