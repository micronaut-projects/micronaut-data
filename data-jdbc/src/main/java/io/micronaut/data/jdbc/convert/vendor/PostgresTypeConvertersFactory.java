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

import com.pgvector.PGvector;
import com.pgvector.PGsparsevec;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.vector.FloatVector;
import io.micronaut.data.model.vector.SparseFloatVector;
import io.micronaut.data.model.vector.Vector;
import io.micronaut.data.runtime.convert.DataTypeConverter;
import org.postgresql.util.PGobject;

import java.sql.SQLException;
import java.util.Optional;

/**
 * Postgres converters for pgvector integration.
 *
 * @author Nemanja Mikic
 * @since 5.0.0
 */
@Factory
@Requires(classes = PGvector.class)
@Internal
final class PostgresTypeConvertersFactory {

    @Prototype
    DataTypeConverter<FloatVector, PGvector> fromFloatVectorToPgVector() {
        return (vector, targetType, context) -> Optional.of(new PGvector(vector.toFloatArray()));
    }

    @Prototype
    DataTypeConverter<Vector, PGvector> fromVectorToPgVector() {
        return (vector, targetType, context) -> Optional.of(new PGvector(vector.toFloatArray()));
    }

    @Prototype
    DataTypeConverter<SparseFloatVector, PGsparsevec> fromSparseFloatVectorToPgSparsevec() {
        return (vector, targetType, context) -> Optional.of(new PGsparsevec(vector.toFloatArray()));
    }

    @Prototype
    DataTypeConverter<PGobject, FloatVector> fromPgObjectToFloatVector() {
        return (pg, targetType, context) -> toDenseFloatArray(pg).map(floats -> (FloatVector) Vector.of(floats));
    }

    @Prototype
    DataTypeConverter<PGobject, Vector> fromPgObjectToVector() {
        return (pg, targetType, context) -> toDenseFloatArray(pg).map(Vector::of);
    }

    @Prototype
    DataTypeConverter<PGobject, SparseFloatVector> fromPgObjectToSparseFloatVector() {
        return (pg, targetType, context) -> toDenseFloatArray(pg).map(SparseFloatVector::fromDense);
    }

    @Prototype
    DataTypeConverter<PGobject, PGvector> fromPgObjectToPgVector() {
        return (pg, targetType, context) -> toDenseFloatArray(pg).map(PGvector::new);
    }

    private static Optional<float[]> toDenseFloatArray(PGobject pg) {
        if (pg == null) {
            return Optional.empty();
        }
        if (pg instanceof PGvector pgvector) {
            return Optional.of(pgvector.toArray());
        }
        if (pg instanceof PGsparsevec sparseVec) {
            return Optional.of(sparseVec.toArray());
        }
        try {
            if ("sparsevec".equalsIgnoreCase(pg.getType())) {
                return Optional.of(new PGsparsevec(pg.getValue()).toArray());
            }
            return Optional.of(new PGvector(pg.getValue()).toArray());
        } catch (SQLException e) {
            return Optional.empty();
        }
    }
}
