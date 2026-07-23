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
package io.micronaut.data.runtime.operations;

import io.micronaut.core.annotation.Internal;
import jakarta.persistence.criteria.CriteriaQuery;
import org.reactivestreams.Publisher;

/**
 * Reactively executes the internal criteria query that selects the IDs for a page containing joins or fetches.
 *
 * @since 5.1
 */
@Internal
public interface ReactivePageIdCriteriaRepositoryOperations {

    /**
     * @param query The page-ID criteria query
     * @param offset The offset
     * @param limit The limit
     * @param <T> The result type
     * @return The page IDs
     */
    <T> Publisher<T> findPageIds(CriteriaQuery<T> query, int offset, int limit);
}
