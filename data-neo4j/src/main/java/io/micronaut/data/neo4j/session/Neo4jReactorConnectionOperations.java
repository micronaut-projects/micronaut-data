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
package io.micronaut.data.neo4j.session;

import io.micronaut.data.connection.reactive.ReactorConnectionOperations;

/**
 * Reactive connection operations for Neo4j, backed by the driver's reactive sessions.
 *
 * @author graemerocher
 * @since 5.3.0
 */
public interface Neo4jReactorConnectionOperations extends ReactorConnectionOperations<Neo4jReactiveConnection> {
}
