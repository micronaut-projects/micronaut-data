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
package io.micronaut.data.model.search;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.data.exceptions.DataAccessException;

/**
 * Thrown when a search query uses a {@link SearchCapability} the backend does not support.
 *
 * @since 5.4.0
 */
@Experimental
public class UnsupportedSearchFeatureException extends DataAccessException {

    private final SearchCapability capability;

    /**
     * @param capability The unsupported capability
     * @param backend A description of the backend, for example the SQL dialect name
     */
    public UnsupportedSearchFeatureException(SearchCapability capability, String backend) {
        super("Search feature " + capability + " is not supported by " + backend);
        this.capability = capability;
    }

    /**
     * @return The unsupported capability
     */
    public SearchCapability getCapability() {
        return capability;
    }
}
