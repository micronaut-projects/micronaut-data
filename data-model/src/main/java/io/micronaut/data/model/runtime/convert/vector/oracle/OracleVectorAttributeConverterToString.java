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
package io.micronaut.data.model.runtime.convert.vector.oracle;

import io.micronaut.context.annotation.Requires;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import oracle.sql.VECTOR;

/**
 * Contract for Oracle vector attribute converters that also provide a string representation of the
 * persisted value.
 *
 * @param <X> The entity type (converted from/to)
 * @param <Y> The persisted JDBC/R2DBC type
 */
@Requires(classes = VECTOR.class)
public interface OracleVectorAttributeConverterToString<X, Y> extends AttributeConverter<X, Y> {

    /**
     * Convert the persisted value to a string representation.
     *
     * @param value The persisted value.
     * @return The string representation.
     */
    String convertToString(Y value);
}
