/*
 * Copyright 2017-2023 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.exceptions.DataAccessException;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * The utility methods for DTO mapper.
 */
@Internal
final class MapperUtils {

    private MapperUtils() {
    }

    /**
     * Converts input value to collection. If input value is collection, iterable or array then converts to collection and if it is single value
     * then creates collection with single value. If input value is null, result is null.
     *
     * @param value the input value to be converted to collection
     * @return the result collection
     */
    static Collection<?> toCollection(Object value) {
        if (value == null) {
            return null;
        }
        Collection<?> collection;
        if (value instanceof Collection) {
            collection = (Collection<?>) value;
        } else if (value instanceof Iterable) {
            collection = new ArrayList<>(CollectionUtils.iterableToList((Iterable) value));
        } else if (value.getClass().isArray()) {
            Object[] arr = (Object[]) value;
            collection = Arrays.asList(arr);
        } else if (value instanceof java.sql.Array) {
            Object[] arr;
            try {
                arr = (Object[]) ((java.sql.Array) value).getArray();
            } catch (SQLException e) {
                throw new DataAccessException("Unable to read SQL array", e);
            }
            collection = Arrays.asList(arr);
        } else {
            collection = Collections.singleton(value);
        }
        return collection;
    }
}
