/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.data.search.document.runtime;

import io.micronaut.data.operations.RepositoryOperations;

/**
 * Repository operations backed by a document search store through a {@link SearchTransport}.
 *
 * @since 5.4.0
 */
public interface DocumentSearchRepositoryOperations extends RepositoryOperations {

    /**
     * @return The transport
     */
    SearchTransport getTransport();

    /**
     * @param entityType The entity type
     * @return The index name the entity is stored in
     */
    String getIndexName(Class<?> entityType);
}
