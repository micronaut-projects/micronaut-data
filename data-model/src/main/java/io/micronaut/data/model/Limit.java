/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.data.model;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;

/**
 * The query limit.
 *
 * @since 4.13
 */
@Experimental
public interface Limit {

    Limit UNLIMITED = Limit.of(-1, 0);

    /**
     * @return The max results of the query or -1 if none
     */
    default int maxResults() {
        return -1;
    }

    /**
     * @return The offset of the query or 0 if none
     */
    default long offset() {
        return 0;
    }

    /**
     * @return Is the limit present
     */
    default boolean isLimited() {
        return maxResults() != -1 || offset() > 0;
    }

    /**
     * Creates a new limit.
     *
     * @param maxResults The max results
     * @param offset     The offset
     * @return the limit
     */
    static Limit of(int maxResults, long offset) {
        return new DefaultLimit(maxResults, offset);
    }

    /**
     * The default implementation.
     *
     * @param maxResults The max results
     * @param offset     The offset
     */
    @Internal
    record DefaultLimit(int maxResults, long offset) implements Limit {
    }

}
