/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.operations;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.model.runtime.StoredQuery;

import java.util.Collections;
import java.util.Map;

/**
 * Hints capable repository interface.
 *
 * @author Denis Stepanov
 * @since 3.3
 */
public interface HintsCapableRepository {

    /**
     * Obtain any custom query hints for this method and repository implementation.
     * @param storedQuery The stored query
     * @return THe query hints
     */
    default @NonNull Map<String, Object> getQueryHints(@NonNull StoredQuery<?, ?> storedQuery) {
        return Collections.emptyMap();
    }

}
