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
package io.micronaut.data.runtime.operations.internal;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;

/**
 * The internal context.
 *
 * @param <Cnt> The connection type
 * @param <PS>  The statement type
 */
@Internal
public interface OpContext<Cnt, PS> {

    /**
     * Get runtime entity registry.
     *
     * @return the registry
     */
    RuntimeEntityRegistry getRuntimeEntityRegistry();

    /**
     * Get persistent entity by type.
     *
     * @param type The type class
     * @param <T>  The type
     * @return persistent entity
     */
    <T> RuntimePersistentEntity<T> getEntity(@NonNull Class<T> type);

    /**
     * Used to define the index whether it is 1 based (JDBC) or 0 based (R2DBC).
     *
     * @param index The index to shift
     * @return the index
     */
    int shiftIndex(int index);

    /**
     * Set the parameter value on the given statement.
     *
     * @param preparedStatement The prepared statement
     * @param index             The index
     * @param dataType          The data type
     * @param value             The value
     * @param dialect           The dialect
     */
    void setStatementParameter(PS preparedStatement, int index, DataType dataType, Object value, Dialect dialect);

    /**
     * Convert the property value if needed.
     *
     * @param connection the connection
     * @param value
     * @param property
     * @return converter value
     */
    Object convert(Cnt connection, Object value, RuntimePersistentProperty<?> property);

    /**
     * Convert the property value if needed.
     *
     * @param converterClass the converter class
     * @param connection     the connection
     * @param value          the value
     * @param argument       the argument
     * @return converter value
     */
    Object convert(Class<?> converterClass, Cnt connection, Object value, @Nullable Argument<?> argument);
}
