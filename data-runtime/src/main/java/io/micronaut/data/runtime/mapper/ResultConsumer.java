/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.data.runtime.mapper;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.exceptions.DataAccessException;

import java.math.BigDecimal;
import java.util.Date;
import java.util.function.BiConsumer;

/**
 * A mapping function can be used as a method parameter to repository methods to allow custom mapping of results.
 *
 * @author graemerocher
 * @since 1.0.0
 *
 * @param <T> The root entity type
 * @param <RS> The result set type
 */
@Introspected
@FunctionalInterface
public interface ResultConsumer<T, RS> extends BiConsumer<T, ResultConsumer.Context<RS>> {

    /**
     * A context object that simplifies mapping results to objects.
     *
     * @param <RS> The result set type.
     */
    interface Context<RS> {

        /**
         * @return The current state of the result.
         */
        RS getResultSet();

        /**
         * @return The result reader.
         */
        ResultReader<RS, String> getResultReader();

        /**
         * Read an entity using the given prefix to be passes to result set lookups.
         * @param prefix The prefix
         * @param type The entity type
         * @param <E> The entity generic type
         * @throws DataAccessException if it is not possible read the result from the result set.
         * @return The entity result
         */
        @NonNull <E> E readEntity(@NonNull String prefix, @NonNull Class<E> type) throws DataAccessException;

        /**
         * Read an entity using the given prefix to be passes to result set lookups.
         * @param prefix The prefix
         * @param rootEntity The entity type
         * @param dtoType The DTO type. Must be annotated with {@link Introspected}
         * @param <E> The entity generic type
         * @param <D> The DTO generic type
         * @throws DataAccessException if it is not possible read the result from the result set.
         * @return The entity result
         */
        @NonNull <E, D> D readDTO(@NonNull String prefix, @NonNull Class<E> rootEntity, @NonNull Class<D> dtoType) throws DataAccessException;

        /**
         * Read a long value for the given name.
         * 
         * @param name The name (such as the column name)
         * @return The long value
         */
        default long readLong(@NonNull String name) {
            return getResultReader().readLong(getResultSet(), name);
        }

        /**
         * Read a char value for the given name.
         * 
         * @param name The name (such as the column name)
         * @return The char value
         */
        default char readChar(@NonNull String name) {
            return getResultReader().readChar(getResultSet(), name);
        }

        /**
         * Read a date value for the given name.
         * 
         * @param name The name (such as the column name)
         * @return The char value
         */
        default @Nullable Date readDate(@NonNull String name) {
            return getResultReader().readDate(getResultSet(), name);
        }

        /**
         * Read a timestamp value for the given index.
         * 
         * @param name The name (such as the column name)
         * @return The char value
         */
        default @Nullable Date readTimestamp(@NonNull String name) {
            return getResultReader().readTimestamp(getResultSet(), name);
        }

        /**
         * Read a string value for the given name.
         * @param name The name (such as the column name)
         * @return The string value
         */
        default @Nullable String readString(@NonNull String name) {
            return getResultReader().readString(getResultSet(), name);
        }

        /**
         * Read a int value for the given name.
         * 
         * @param name The name (such as the column name)
         * @return The int value
         */
        default int readInt(@NonNull String name) {
            return getResultReader().readInt(getResultSet(), name);
        }

        /**
         * Read a boolean value for the given name.
         * 
         * @param name The name (such as the column name)
         * @return The boolean value
         */
        default boolean readBoolean(@NonNull String name) {
            return getResultReader().readBoolean(getResultSet(), name);
        }

        /**
         * Read a float value for the given name.
         * 
         * @param name The name (such as the column name)
         * @return The float value
         */
        default float readFloat(@NonNull String name) {
            return getResultReader().readFloat(getResultSet(), name);
        }

        /**
         * Read a byte value for the given name.
         * 
         * @param name The name (such as the column name)
         * @return The byte value
         */
        default byte readByte(@NonNull String name) {
            return getResultReader().readByte(getResultSet(), name);
        }

        /**
         * Read a short value for the given name.
         * 
         * @param name The name (such as the column name)
         * @return The short value
         */
        default short readShort(@NonNull String name) {
            return getResultReader().readShort(getResultSet(), name);
        }

        /**
         * Read a double value for the given name.
         * 
         * @param name The name (such as the column name)
         * @return The double value
         */
        default double readDouble(@NonNull String name) {
            return getResultReader().readDouble(getResultSet(), name);
        }

        /**
         * Read a BigDecimal value for the given name.
         * 
         * @param name The name (such as the column name)
         * @return The BigDecimal value
         */
        default @Nullable BigDecimal readBigDecimal(@NonNull String name) {
            return getResultReader().readBigDecimal(getResultSet(), name);
        }

        /**
         * Read a byte[] value for the given name.
         * 
         * @param name The name (such as the column name)
         * @return The byte[] value
         */
        default byte[] readBytes(@NonNull String name) {
            return getResultReader().readBytes(getResultSet(), name);
        }

    }
}
