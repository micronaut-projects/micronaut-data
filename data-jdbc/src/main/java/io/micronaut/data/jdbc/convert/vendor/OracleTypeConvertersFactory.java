/*
 * Copyright 2017-2021 original authors
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

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.runtime.convert.vector.impl.AbstractOracleTypeConvertersFactory;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.vector.Vector;
import io.micronaut.data.model.vector.ByteVector;
import io.micronaut.data.model.vector.DoubleVector;
import io.micronaut.data.model.vector.FloatVector;
import io.micronaut.data.runtime.convert.DataTypeConverter;
import oracle.jdbc.OracleType;
import oracle.sql.DATE;
import oracle.sql.TIMESTAMP;
import oracle.sql.VECTOR;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Oracle DATE and VECTOR converters.
 *
 * @author Denis Stepanov
 * @since 3.1.1
 */
@Factory
@Requires(classes = DATE.class)
@Internal
final class OracleTypeConvertersFactory extends AbstractOracleTypeConvertersFactory {

    @Prototype
    DataTypeConverter<DATE, Timestamp> fromOracleDateToTimestamp() {
        return (date, targetType, context) -> Optional.of(date.timestampValue());
    }

    @Prototype
    DataTypeConverter<DATE, LocalDateTime> fromOracleDateToLocalDateTime() {
        return (date, targetType, context) -> Optional.of(date.timestampValue().toLocalDateTime());
    }

    @Prototype
    DataTypeConverter<DATE, Instant> fromOracleDateToInstant() {
        return (date, targetType, context) -> Optional.of(date.timestampValue().toInstant());
    }

    @Prototype
    DataTypeConverter<TIMESTAMP, Timestamp> fromOracleTimestampToTimestamp() {
        return (timestamp, targetType, context) -> {
            try {
                return Optional.of(timestamp.timestampValue());
            } catch (SQLException e) {
                throw new DataAccessException("Cannot extract timestamp from: " + timestamp);
            }
        };
    }

    @Prototype
    DataTypeConverter<TIMESTAMP, LocalDateTime> fromOracleTimestampToLocalDateTime() {
        return (timestamp, targetType, context) -> {
            try {
                return Optional.of(timestamp.timestampValue().toLocalDateTime());
            } catch (SQLException e) {
                throw new DataAccessException("Cannot extract timestamp from: " + timestamp);
            }
        };
    }

    @Prototype
    DataTypeConverter<TIMESTAMP, Instant> fromOracleTimestampToInstant() {
        return (timestamp, targetType, context) -> {
            try {
                return Optional.of(timestamp.timestampValue().toInstant());
            } catch (SQLException e) {
                throw new DataAccessException("Cannot extract timestamp from: " + timestamp);
            }
        };
    }

    @Prototype
    DataTypeConverter<VECTOR, Vector> fromOracleVectorToVector() {
        return (oracleVector, targetType, context) -> {
            OracleVectorAdapter adapter = new OracleVectorAdapterImpl(oracleVector);
            return Optional.of(toVector(adapter));
        };
    }

    @Prototype
    DataTypeConverter<Vector, VECTOR> fromVectorToOracleVector() {
        return (vector, targetType, context) -> {
            try {
                if (vector instanceof FloatVector floatVector) {
                    return Optional.of(VECTOR.ofFloat32Values(floatVector.toFloatArray()));
                }
                if (vector instanceof ByteVector byteVector) {
                    return Optional.of(VECTOR.ofInt8Values(byteVector.toByteArray()));
                }
                return Optional.of(VECTOR.ofFloat64Values(vector.toDoubleArray()));
            } catch (SQLException e) {
                throw new DataAccessException("Cannot convert Vector to oracle.sql.VECTOR: " + vector, e);
            }
        };
    }

    @Prototype
    @Requires(classes = VECTOR.class)
    DataTypeConverter<VECTOR, DoubleVector> fromOracleVectorToDoubleVector() {
        return (oracleVector, targetType, context) -> {
            OracleVectorAdapter adapter = new OracleVectorAdapterImpl(oracleVector);
            return vectorToDoubleArray(adapter).map(a -> (DoubleVector) Vector.of(a));
        };
    }

    @Prototype
    @Requires(classes = VECTOR.class)
    DataTypeConverter<VECTOR, FloatVector> fromOracleVectorToFloatVector() {
        return (oracleVector, targetType, context) -> {
            OracleVectorAdapter adapter = new OracleVectorAdapterImpl(oracleVector);
            return vectorToFloatArray(adapter).map(a -> (FloatVector) Vector.of(a));
        };
    }

    @Prototype
    @Requires(classes = VECTOR.class)
    DataTypeConverter<VECTOR, ByteVector> fromOracleVectorToByteVector() {
        return (oracleVector, targetType, context) -> {
            OracleVectorAdapter adapter = new OracleVectorAdapterImpl(oracleVector);
            return vectorToByteArray(adapter).map(a -> (ByteVector) Vector.of(a));
        };
    }

    // Adapter over oracle.sql.VECTOR to reuse shared helpers from AbstractOracleTypeConvertersFactory
    private static final class OracleVectorAdapterImpl implements OracleVectorAdapter {
        private final VECTOR v;

        OracleVectorAdapterImpl(VECTOR v) {
            this.v = v;
        }

        @Override
        public OracleVectorKind getKind() {
            OracleType t;
            try {
                t = v.getType();
            } catch (SQLException e) {
                throw new DataAccessException("Cannot inspect vector type: " + v);
            }
            return switch (t) {
                case VECTOR_FLOAT32 -> OracleVectorKind.FLOAT32;
                case VECTOR_FLOAT64 -> OracleVectorKind.FLOAT64;
                case VECTOR_INT8 -> OracleVectorKind.INT8;
                case VECTOR_BINARY -> OracleVectorKind.BINARY;
                default -> throw new DataAccessException("Unknown Oracle VECTOR type: " + t);
            };
        }

        @Override
        public float[] toFloatArray() {
            try {
                return v.toFloatArray();
            } catch (SQLException e) {
                throw new DataAccessException("Cannot extract vector from: " + v);
            }
        }

        @Override
        public double[] toDoubleArray() {
            try {
                return v.toDoubleArray();
            } catch (SQLException e) {
                throw new DataAccessException("Cannot extract vector from: " + v);
            }
        }

        @Override
        public byte[] toByteArray() {
            try {
                return v.toByteArray();
            } catch (SQLException e) {
                throw new DataAccessException("Cannot extract vector from: " + v);
            }
        }
    }
}
