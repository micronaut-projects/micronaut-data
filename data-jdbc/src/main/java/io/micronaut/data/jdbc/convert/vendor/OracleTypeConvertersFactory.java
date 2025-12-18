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
 * Oracle DATE converters.
 *
 * @author Denis Stepanov
 * @since 3.1.1
 */
@Factory
@Requires(classes = DATE.class)
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
    DataTypeConverter<DoubleVector, double[]> fromVectorDoubleToArray() {
        return (vector, targetType, context) -> Optional.of(vector.toDoubleArray());
    }

    @Prototype
    DataTypeConverter<FloatVector, float[]> fromVectorFloatToArray() {
        return (vector, targetType, context) -> Optional.of(vector.toFloatArray());
    }

    @Prototype
    DataTypeConverter<ByteVector, byte[]> fromVectorByteToArray() {
        return (vector, targetType, context) -> Optional.of(vector.toByteArray());
    }

    // ----------------------
    // Write path: Vector and arrays -> String (acceptable by Oracle JDBC)
    // ----------------------

    @Prototype
    DataTypeConverter<Vector, String> fromVectorToString() {
        return (vector, targetType, context) -> Optional.of(toOracleText(vector));
    }

    @Prototype
    DataTypeConverter<DoubleVector, String> fromDoubleVectorToString() {
        return (vector, targetType, context) -> Optional.of(toOracleText(vector.toDoubleArray()));
    }

    @Prototype
    DataTypeConverter<FloatVector, String> fromFloatVectorToString() {
        return (vector, targetType, context) -> Optional.of(toOracleText(vector.toFloatArray()));
    }

    @Prototype
    DataTypeConverter<ByteVector, String> fromByteVectorToString() {
        return (vector, targetType, context) -> Optional.of(toOracleText(vector.toByteArray()));
    }

    // ----------------------
    // Read path: String -> typed Vector implementations (Oracle textual format e.g. "[1.0, 2.0]")
    // ----------------------

    @Prototype
    DataTypeConverter<String, Vector> fromStringToVector() {
        return (text, targetType, context) -> Optional.of(Vector.of(parseDoubleArray(text)));
    }

    @Prototype
    DataTypeConverter<String, DoubleVector> fromStringToDoubleVector() {
        return (text, targetType, context) -> Optional.of((DoubleVector) Vector.of(parseDoubleArray(text)));
    }

    @Prototype
    DataTypeConverter<String, FloatVector> fromStringToFloatVector() {
        return (text, targetType, context) -> Optional.of((FloatVector) Vector.of(parseFloatArray(text)));
    }

    @Prototype
    DataTypeConverter<String, ByteVector> fromStringToByteVector() {
        return (text, targetType, context) -> Optional.of((ByteVector) Vector.of(parseByteArray(text)));
    }





    // ----------------------
    // Read path: oracle.sql.VECTOR -> typed Vector implementations
    // ----------------------

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
