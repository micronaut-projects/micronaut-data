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
import io.micronaut.data.model.vector.Vector;
import io.micronaut.data.runtime.convert.DataTypeConverter;
import org.postgresql.util.PGobject;

import java.sql.SQLException;
import java.util.Optional;

/**
 * Postgres converters for pgvector integration.
 *
 * These converters are registered only when the Postgres JDBC driver is on the classpath
 * and the configured dialect is POSTGRES. They convert Micronaut's Vector types (and primitive
 * arrays when used as parameters) into PGobject with type "vector", which the PostgreSQL driver
 * understands for the pgvector extension.
 *
 * NOTE:
 * - We keep generic (dialect-agnostic) converters in data-model intact.
 * - This factory avoids adding a hard dependency to the driver in data-model.
 * - Binding still goes through JdbcQueryStatement#setValue(..) which calls setObject(..) and
 *   will pass the PGobject to the driver as-is.
 *
 * @author Nemanja Mikic
 * @since 5.0.0
 */
@Factory
@Requires(classes = PGvector.class)
@Internal
final class PostgresTypeConvertersFactory {

    private static final int MIN_SPARSE_DIMENSIONS = 16;
    private static final int MIN_ZERO_RATIO_DENOMINATOR = 4;

    @Prototype
    DataTypeConverter<FloatVector, PGobject> fromFloatVectorToPgObject() {
        return (vector, targetType, context) -> toPgObject(vector.toFloatArray());
    }

    @Prototype
    DataTypeConverter<float[], PGobject> fromFloatArrayToPgObject() {
        return (arr, targetType, context) -> toPgObject(arr);
    }

    @Prototype
    DataTypeConverter<PGobject, FloatVector> fromPgObjectToFloatVector() {
        return (pg, targetType, context) -> {
            Optional<float[]> dense = toDenseFloatArray(pg);
            return dense.map(floats -> (FloatVector) Vector.of(floats));
        };
    }

    @Prototype
    DataTypeConverter<PGobject, Vector> fromPgObjectToVector() {
        return (pg, targetType, context) -> {
            Optional<float[]> dense = toDenseFloatArray(pg);
            return dense.map(Vector::of);
        };
    }

    @Prototype
    DataTypeConverter<PGobject, PGvector> fromPgObjectToPgVector() {
        return (pg, targetType, context) -> {
            if (pg == null) {
                return Optional.empty();
            }
            try {
                if ("sparsevec".equalsIgnoreCase(pg.getType())) {
                    return Optional.of(new PGvector(new PGsparsevec(pg.getValue()).toArray()));
                }
                return Optional.of(new PGvector(pg.getValue()));
            } catch (SQLException e) {
                return Optional.empty();
            }
        };
    }

    private static Optional<PGobject> toPgObject(float[] arr) {
        try {
            if (shouldSerializeAsSparse(arr)) {
                return Optional.of(new PGsparsevec(arr));
            }
            PGobject pg = new PGobject();
            pg.setType("vector");
            pg.setValue(new PGvector(arr).toString());
            return Optional.of(pg);
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    private static Optional<float[]> toDenseFloatArray(PGobject pg) {
        if (pg == null) {
            return Optional.empty();
        }
        if (pg instanceof PGvector pgvector) {
            return Optional.of(pgvector.toArray());
        }
        if (pg instanceof PGsparsevec pGsparsevec) {
            return Optional.of(pGsparsevec.toArray());
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

    private static boolean shouldSerializeAsSparse(float[] arr) {
        if (arr.length < MIN_SPARSE_DIMENSIONS) {
            return false;
        }
        int nonZero = 0;
        for (float v : arr) {
            if (v != 0f) {
                nonZero++;
            }
        }
        return nonZero * MIN_ZERO_RATIO_DENOMINATOR <= arr.length;
    }

}
