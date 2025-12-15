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
package io.micronaut.data.r2dbc.convert.vendor;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.runtime.convert.vector.impl.AbstractOracleTypeConvertersFactory;
import io.micronaut.data.model.vector.ByteVector;
import io.micronaut.data.model.vector.DoubleVector;
import io.micronaut.data.model.vector.FloatVector;
import io.micronaut.data.model.vector.IntVector;
import io.micronaut.data.model.vector.Vector;
import io.micronaut.data.runtime.convert.DataTypeConverter;
import oracle.jdbc.OracleType;
import oracle.sql.VECTOR;

import java.sql.SQLException;
import java.util.Optional;

/**
 * Oracle DATE converters.
 *
 * @author Nemanja Mikic
 * @since 5.0.0
 */
@Factory
@Requires(classes = VECTOR.class)
final class OracleTypeConvertersFactory extends AbstractOracleTypeConvertersFactory {

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
    DataTypeConverter<IntVector, int[]> fromVectorIntToArray() {
        return (vector, targetType, context) -> Optional.of(vector.toIntegerArray());
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
    DataTypeConverter<IntVector, String> fromIntVectorToString() {
        return (vector, targetType, context) -> Optional.of(toOracleText(vector.toIntegerArray()));
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
    DataTypeConverter<String, IntVector> fromStringToIntVector() {
        return (text, targetType, context) -> Optional.of((IntVector) Vector.of(parseIntArray(text)));
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
    DataTypeConverter<VECTOR, IntVector> fromOracleVectorToIntVector() {
        return (oracleVector, targetType, context) -> {
            OracleVectorAdapter adapter = new OracleVectorAdapterImpl(oracleVector);
            return vectorToIntArray(adapter).map(a -> (IntVector) Vector.of(a));
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
        public int[] toIntArray() {
            try {
                return v.toIntArray();
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
