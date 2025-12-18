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
package io.micronaut.data.jdbc.convert.vendor;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.runtime.convert.vector.VectorTypeConvertor;
import io.micronaut.data.model.vector.ByteVector;
import io.micronaut.data.model.vector.DoubleVector;
import io.micronaut.data.model.vector.Vector;
import jakarta.inject.Named;
import jakarta.inject.Singleton;


/**
 * MySQL-specific {@link VectorTypeConvertor} that maps {@link Vector} to JDBC binary (byte[]) accepted
 * by MySQL HeatWave VECTOR (float32 little-endian, plain concatenation), and back.
 *
 * Persisted type: {@code byte[]} (float32 LE per element)
 *
 * Example: (1.0, 2.0, 3.0) -> bytes: 00 00 80 3F  00 00 00 40  00 00 40 40
 *
 * @author Nemanja Mikic
 * @since 5.0.0
 */
@Internal
@Requires(classes = com.mysql.cj.jdbc.Driver.class)
@Singleton
@Named("MYSQL")
public class MySqlJdbcVectorConvertor implements VectorTypeConvertor<byte[]> {

    private final ConversionService conversionService;

    public MySqlJdbcVectorConvertor(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Override
    public Class<byte[]> getPersistedType() {
        return byte[].class;
    }

    @Override
    public byte[] convert(Vector vector, Class<byte[]> targetType) {
        if (vector instanceof ByteVector || vector instanceof DoubleVector) {
            throw new IllegalArgumentException(getName() + " does not support " + vector.getClass().getName());
        }
        return conversionService.convert(vector, targetType).orElse(null);
    }

    @Override
    public Vector convert(byte[] object, Class<Vector> targetType) {
        if (targetType.getName().equals(ByteVector.class.getName()) || targetType.getName().equals(DoubleVector.class.getName())) {
            throw new IllegalArgumentException(getName() + " does not support " + targetType.getName());
        }
        return conversionService.convert(object, targetType).orElse(null);
    }

    @Override
    public Dialect getDialect() {
        return Dialect.MYSQL;
    }

    @Override
    public @NonNull String getName() {
        return getDialect().toString();
    }

}
