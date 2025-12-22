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
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.model.runtime.convert.DatabaseType;
import io.micronaut.data.model.runtime.convert.vector.VectorTypeConverter;
import io.micronaut.data.model.vector.ByteVector;
import io.micronaut.data.model.vector.DoubleVector;
import io.micronaut.data.model.vector.FloatVector;
import io.micronaut.data.model.vector.Vector;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Oracle-specific {@link VectorTypeConverter} that maps {@link Vector} to the JDBC {@link String} representation
 * accepted by the Oracle driver and back.
 *
 * @author Nemanja Mikic
 * @since 5.0.0
 */
@Internal
@Singleton
public final class OracleJdbcVectorConverter extends AbstractJdbcVectorConverter<String> {

    public OracleJdbcVectorConverter(ConversionService conversionService) {
        super(conversionService);
    }

    @Override
    public Class<String> getPersistedType() {
        return String.class;
    }

    public List<Class<? extends Vector>> supportedVectorTypes() {
        return List.of(Vector.class, DoubleVector.class, FloatVector.class, ByteVector.class);
    }

        @Override
    public DatabaseType databaseType() {
        return DatabaseType.ORACLE;
    }
}
