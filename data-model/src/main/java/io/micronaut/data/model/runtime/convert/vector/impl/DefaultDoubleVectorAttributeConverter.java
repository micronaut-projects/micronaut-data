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
package io.micronaut.data.model.runtime.convert.vector.impl;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.runtime.convert.vector.DoubleVectorAttributeConverter;
import io.micronaut.data.model.runtime.convert.vector.VectorTypeConvertor;
import io.micronaut.data.model.vector.DoubleVector;
import jakarta.inject.Singleton;

import java.util.Map;

/**
 * Unified attribute converter for DoubleVector that supports multiple SQL dialects.
 * - PostgreSQL: persisted value is a {@code org.postgresql.util.PGobject} of type {@code vector}
 * - Oracle: persisted value is a {@code String} accepted by the Oracle JDBC driver (e.g. "[1.0, 2.0]")
 *
 * This single converter replaces the previous dialect-specific converters and selects
 * the persisted representation based on the {@link Dialect} obtained from the {@link ConversionContext}.
 *
 * @author Nemanja Mikic
 * @since 5.0.0
 */
@Singleton
public class DefaultDoubleVectorAttributeConverter extends AbstractVectorAttributeConverter<DoubleVector, Object> implements DoubleVectorAttributeConverter<Object> {

    protected DefaultDoubleVectorAttributeConverter(Map<String, VectorTypeConvertor> converterMap) {
        super(converterMap, DoubleVector.class);
    }

    @Override
    String getOracleType() {
        return "FLOAT64";
    }
}
