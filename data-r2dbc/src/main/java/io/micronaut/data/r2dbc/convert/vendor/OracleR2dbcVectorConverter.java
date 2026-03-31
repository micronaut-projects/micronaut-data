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
package io.micronaut.data.r2dbc.convert.vendor;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.model.runtime.convert.DatabaseType;
import io.micronaut.data.model.vector.ByteVector;
import io.micronaut.data.model.vector.DoubleVector;
import io.micronaut.data.model.vector.FloatVector;
import io.micronaut.data.model.vector.SparseByteVector;
import io.micronaut.data.model.vector.SparseDoubleVector;
import io.micronaut.data.model.vector.SparseFloatVector;
import io.micronaut.data.model.vector.Vector;
import jakarta.inject.Singleton;
import oracle.sql.VECTOR;

import java.util.Set;

/**
 * VectorTypeConverter for Oracle R2DBC.
 * Persists Micronaut Vector instances as oracle.sql.VECTOR values.
 *
 * @author Nemanja Mikic
 * @since 5.0.0
 */
@Internal
@Singleton
@Requires(classes = VECTOR.class)
final class OracleR2dbcVectorConverter extends AbstractR2dbcVectorConverter<VECTOR> {

    OracleR2dbcVectorConverter(ConversionService conversionService) {
        super(conversionService);
    }

    @Override
    public Set<Class<? extends Vector>> supportedVectorTypes() {
        return Set.of(
            Vector.class,
            DoubleVector.class,
            FloatVector.class,
            ByteVector.class,
            SparseDoubleVector.class,
            SparseFloatVector.class,
            SparseByteVector.class
        );
    }

    @Override
    public DatabaseType databaseType() {
        return DatabaseType.ORACLE;
    }

    @Override
    public Class<VECTOR> getPersistedType() {
        return VECTOR.class;
    }
}
