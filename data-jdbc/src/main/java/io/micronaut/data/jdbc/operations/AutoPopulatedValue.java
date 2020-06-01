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
package io.micronaut.data.jdbc.operations;

/**
 * Response from {@link AutoPopulatedGenerator#valueAtIndex(int, io.micronaut.data.model.query.builder.sql.Dialect)}.
 * @author Sergio del Amo
 * @since 1.1.0
 */
public class AutoPopulatedValue {

    Object value;
    WriteValueMode writeValueMode;
    private boolean needsConversion;

    /**
     * Constructor.
     */
    public AutoPopulatedValue() {
    }

    /**
     *
     * @param value the value which will be populated
     * @param mode The way to write the value in the query statement.
     */
    public AutoPopulatedValue(Object value, WriteValueMode mode) {
        this.value = value;
        this.writeValueMode = mode;
    }

    public AutoPopulatedValue(Object value, WriteValueMode mode, boolean needsConversion) {
        this(value, mode);
        this.needsConversion = needsConversion;
    }

    /**
     *
     * @return the value which will be populated
     */
    public Object getValue() {
        return value;
    }

    /**
     *
     * @param value the value which will be populated
     */
    public void setValue(Object value) {
        this.value = value;
    }

    /**
     *
     * @return The way to write the value in the query statement. E.g. Should it call {@link io.micronaut.data.runtime.mapper.QueryStatement#setString(Object, Object, String)}  or {@link io.micronaut.data.runtime.mapper.QueryStatement#setDynamic(Object, Object, io.micronaut.data.model.DataType, Object)}
     */
    public WriteValueMode getWriteValueMode() {
        return writeValueMode;
    }

    /**
     *
     * @param writeValueMode The way to write the value in the query statement.
     */
    public void setWriteValueMode(WriteValueMode writeValueMode) {
        this.writeValueMode = writeValueMode;
    }

    /**
     *
     * @return Whether the value needs to be converted before set
     */
    public boolean needsConversion() {
        return this.needsConversion;
    }

    /**
     *
     * @param needsConversion Whether the value needs to be converted before set
     */
    public void setNeedsConversion(boolean needsConversion) {
        this.needsConversion = needsConversion;
    }
}
