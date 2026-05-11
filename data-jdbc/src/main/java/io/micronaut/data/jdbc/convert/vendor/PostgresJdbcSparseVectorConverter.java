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

import com.pgvector.PGsparsevec;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.model.runtime.convert.DatabaseType;
import io.micronaut.data.model.runtime.convert.vector.VectorTypeConverter;
import io.micronaut.data.model.vector.FloatVector;
import io.micronaut.data.model.vector.SparseFloatVector;
import io.micronaut.data.model.vector.Vector;
import jakarta.inject.Singleton;
import org.postgresql.util.PGobject;

import java.sql.SQLException;
import java.util.Set;

/**
 * PostgreSQL-specific sparse {@link VectorTypeConverter} that maps sparse vectors to {@link PGsparsevec}
 * and back.
 *
 * @author Nemanja Mikic
 * @since 5.0.0
 */
@Internal
@Singleton
@Requires(classes = PGsparsevec.class)
final class PostgresJdbcSparseVectorConverter extends AbstractJdbcVectorConverter<PGobject> {

    PostgresJdbcSparseVectorConverter(ConversionService conversionService) {
        super(conversionService);
    }

    @Override
    public Class<PGobject> getPersistedType() {
        return PGobject.class;
    }

    @Override
    public PGobject convert(Vector vector) {
        if (supportedVectorTypes().stream().anyMatch(x -> x.isAssignableFrom(vector.getClass()))) {
            return new PGsparsevec(vector.toFloatArray());
        }
        throw new IllegalArgumentException(databaseType() + " does not support " + vector.getClass().getName());
    }

    @Override
    public Set<Class<? extends Vector>> supportedVectorTypes() {
        return Set.of(SparseFloatVector.class, Vector.class, FloatVector.class);
    }

    @Override
    public Vector convert(PGobject object, Class<Vector> targetType) {
        float[] values = toArray(object);
        if (SparseFloatVector.class.isAssignableFrom(targetType)) {
            return SparseFloatVector.fromDense(values);
        }
        if (FloatVector.class.isAssignableFrom(targetType) || Vector.class.equals(targetType)) {
            return Vector.of(values);
        }
        throw new IllegalArgumentException(databaseType() + " does not support " + targetType.getName());
    }

    private static float[] toArray(PGobject object) {
        if (object instanceof PGsparsevec sparsevec) {
            return sparsevec.toArray();
        }
        try {
            return new PGsparsevec(object.getValue()).toArray();
        } catch (SQLException e) {
            throw new IllegalArgumentException("Cannot parse PostgreSQL sparse vector value", e);
        }
    }

    @Override
    public DatabaseType databaseType() {
        return DatabaseType.POSTGRES;
    }

    @Override
    public boolean isSparseSupported() {
        return true;
    }
}
