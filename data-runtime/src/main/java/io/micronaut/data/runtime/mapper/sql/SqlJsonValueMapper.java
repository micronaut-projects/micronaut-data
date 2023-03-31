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
package io.micronaut.data.runtime.mapper.sql;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.model.JsonDataType;
import io.micronaut.data.runtime.operations.internal.sql.SqlStoredQuery;
import io.micronaut.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * The SQL JSON parameter value mapper. Default implementation which converts object to a JSON string.
 *
 * @author radovanradic
 * @since 4.0.0
 */
public interface SqlJsonValueMapper {

    /**
     * Converts an object to JSON using json mapper.
     * Default implementation produces JSON string, other implementation may return byte array etc.
     *
     * @param object object of to convert to JSON
     * @param jsonDataType the JSON representation object needs to be serialized to
     * @return the JSON created from the object using json mapper
     * @throws IOException exception that can be thrown when encoding JSON
     */
    default Object mapValue(Object object, JsonDataType jsonDataType) throws IOException {
        if (object == null) {
            return null;
        }
        // This doesn't support writing to blob
        if (object instanceof String) {
            // No need to serialize String
            return object;
        }
        return new String(getJsonMapper().writeValueAsBytes(object), StandardCharsets.UTF_8);
    }

    /**
     * Gets an indicator telling whether mapper can map parameter value to JSON for given
     * SQL stored query and parameter and object being mapped.
     *
     * @param sqlStoredQuery the SQL stored query being executed that needs to convert JSON parameter
     * @param jsonDataType the JSON representation type
     * @return true if mapper can map parameter to JSON in context of given SQL stored query
     */
    default boolean supportsMapValue(SqlStoredQuery<?, ?> sqlStoredQuery, JsonDataType jsonDataType) {
        return true;
    }

    /**
     * @return the json mapper
     */
    @NonNull
    JsonMapper getJsonMapper();
}
