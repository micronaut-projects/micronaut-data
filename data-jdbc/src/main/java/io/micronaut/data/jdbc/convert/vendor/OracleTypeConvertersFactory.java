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
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.Vector;
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
final class OracleTypeConvertersFactory {

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
            try {
                OracleType type = oracleVector.getType();
                switch (type) {
                    case VECTOR_FLOAT32 -> {
                        return Optional.of(Vector.of(oracleVector.toFloatArray()));
                    }
                    case VECTOR_FLOAT64 -> {
                        return Optional.of(Vector.of(oracleVector.toDoubleArray()));
                    }
                    case VECTOR_INT8 -> {
                        return Optional.of(Vector.of(oracleVector.toIntArray()));
                    }
                    case VECTOR_BINARY -> {
                        return Optional.of(Vector.of(oracleVector.toByteArray()));
                    }
                    default -> throw new DataAccessException("Cannot extract vector from: " + oracleVector);
                }
            } catch (SQLException e) {
                throw new DataAccessException("Cannot extract vector from: " + oracleVector);
            }
        };
    }

}
