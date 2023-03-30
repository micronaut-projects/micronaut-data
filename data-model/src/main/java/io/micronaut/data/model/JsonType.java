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
package io.micronaut.data.model;

/**
 * Enum of JSON type representations or actually storage types. It is useful to know how to store and retrieve JSON fields.
 *
 * @author radovanradic
 * @since 4.0.0
 * @see PersistentProperty#getJsonType()
 */
public enum JsonType {
    /**
     * Usually stored in JSON specific column type supported by all databases.
     */
    NATIVE,

    /**
     * Stored in CLOB without conversion.
     */
    STRING,
    /**
     * JSON value stored in BLOB. Supported by Oracle, in other databases basically just byte array.
     */
    BLOB
}
