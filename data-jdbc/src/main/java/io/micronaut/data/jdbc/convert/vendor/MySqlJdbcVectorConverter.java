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
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.model.runtime.convert.DatabaseType;
import io.micronaut.data.model.runtime.convert.vector.VectorTypeConverter;
import io.micronaut.data.model.vector.FloatVector;
import io.micronaut.data.model.vector.Vector;
import jakarta.inject.Singleton;

import java.util.Set;


/**
 * MySQL-specific {@link VectorTypeConverter} that maps {@link Vector} to JDBC binary (byte[]) accepted
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
final class MySqlJdbcVectorConverter extends AbstractJdbcVectorConverter<byte[]> {

    MySqlJdbcVectorConverter(ConversionService conversionService) {
        super(conversionService);
    }

    @Override
    public Class<byte[]> getPersistedType() {
        return byte[].class;
    }

    @Override
    public Set<Class<? extends Vector>> supportedVectorTypes() {
        return Set.of(Vector.class, FloatVector.class);
    }

    @Override
    public byte[] convert(Vector vector) {
        if (!(vector instanceof FloatVector)) {
            throw new IllegalArgumentException(databaseType() + " does not support " + vector.getClass().getName());
        }
        return super.convert(vector);
    }

    @Override
    public DatabaseType databaseType() {
        return DatabaseType.MYSQL;
    }
}
