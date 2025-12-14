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
package io.micronaut.data.model.runtime.convert;

/**
 * SPI for reading typed values from a result set using a conversion-aware reader.
 *
 * @param <RS> The result set type
 * @param <IDX> The column identifier type (index or label)
 * @author Nemanja Mikic
 * @since 5.0.0
 */
public interface ConverterResultReader<RS, IDX> {

    /**
     * Read a value from the underlying result set and convert it to the requested Java type.
     *
     * @param rs   the native result set/row object
     * @param idx  the column index or label
     * @param type the Java type to convert to
     * @return the value converted to the requested type, or {@code null} if the column is SQL NULL
     * @since 5.0.0
     */
    Object readConverter(RS rs, IDX idx, Class<?> type);
}
