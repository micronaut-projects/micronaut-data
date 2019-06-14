/*
 * Copyright 2017-2019 original authors
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
 * Enum of basic data types allowing compile time computation which can then subsequently be used at runtime for fast
 * switching.
 *
 * @author graemerocher
 * @since 1.0.0
 * @see PersistentProperty#getDataType()
 */
public enum DataType {
    /**
     * A big decimal such as {@link java.math.BigDecimal}.
     */
    BIGDECIMAL,
    /**
     * A boolean value.
     */
    BOOLEAN,
    /**
     * A byte.
     */
    BYTE,
    /**
     * A byte array. Often stored as binary.
     */
    BYTE_ARRAY,
    /**
     * A character.
     */
    CHARACTER,
    /**
     * A date such as {@link java.util.Date} or {@link java.time.LocalDate}.
     */
    DATE,
    /**
     * A timestamp such as {@link java.sql.Timestamp} or {@link java.time.Instant}.
     */
    TIMESTAMP,
    /**
     * A {@link Double} value.
     */
    DOUBLE,
    /**
     * A {@link Float} value.
     */
    FLOAT,
    /**
     * A {@link Integer} value.
     */
    INTEGER,
    /**
     * A {@link Long} value.
     */
    LONG,
    /**
     * A {@link Short} value.
     */
    SHORT,
    /**
     * A {@link String} value.
     */
    STRING,
    /**
     * An object of an indeterminate type.
     */
    OBJECT,
    /**
     * A class annotated with {@link io.micronaut.data.annotation.MappedEntity}.
     */
    ENTITY
}
