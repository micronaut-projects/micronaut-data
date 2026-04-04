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
package io.micronaut.data.model.runtime;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.data.model.DataType;
import org.jspecify.annotations.NonNull;

/**
 * Runtime OUT parameter binding metadata for a stored query (e.g. Oracle RETURNING ... INTO ...).
 * Mirrors the builder-time metadata and is used by repository operations to register
 * CallableStatement OUT parameters in correct order and JDBC type.
 *
 * @since 5.0
 */
@Experimental
public interface QueryOutParameterBinding {

    /**
     * @return The name of the OUT column/parameter if available.
     */
    @NonNull
    String name();

    /**
     * @return The data type for the OUT parameter when known.
     */
    @NonNull
    DataType dataType();
}
