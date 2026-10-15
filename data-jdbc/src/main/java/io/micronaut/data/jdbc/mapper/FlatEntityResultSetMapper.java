/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.jdbc.mapper;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.reflect.exception.InstantiationException;
import io.micronaut.core.type.Argument;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.runtime.mapper.sql.SqlTypeMapper;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static io.micronaut.data.jdbc.mapper.FlatEntityMappingPlan.BIG_DECIMAL;
import static io.micronaut.data.jdbc.mapper.FlatEntityMappingPlan.BIG_INTEGER;
import static io.micronaut.data.jdbc.mapper.FlatEntityMappingPlan.BOOLEAN;
import static io.micronaut.data.jdbc.mapper.FlatEntityMappingPlan.BOXED_BOOLEAN;
import static io.micronaut.data.jdbc.mapper.FlatEntityMappingPlan.BOXED_BYTE;
import static io.micronaut.data.jdbc.mapper.FlatEntityMappingPlan.BOXED_DOUBLE;
import static io.micronaut.data.jdbc.mapper.FlatEntityMappingPlan.BOXED_FLOAT;
import static io.micronaut.data.jdbc.mapper.FlatEntityMappingPlan.BOXED_INT;
import static io.micronaut.data.jdbc.mapper.FlatEntityMappingPlan.BOXED_LONG;
import static io.micronaut.data.jdbc.mapper.FlatEntityMappingPlan.BOXED_SHORT;
import static io.micronaut.data.jdbc.mapper.FlatEntityMappingPlan.BYTE;
import static io.micronaut.data.jdbc.mapper.FlatEntityMappingPlan.BYTES;
import static io.micronaut.data.jdbc.mapper.FlatEntityMappingPlan.DOUBLE;
import static io.micronaut.data.jdbc.mapper.FlatEntityMappingPlan.ENUM_NAME;
import static io.micronaut.data.jdbc.mapper.FlatEntityMappingPlan.ENUM_ORDINAL;
import static io.micronaut.data.jdbc.mapper.FlatEntityMappingPlan.FLOAT;
import static io.micronaut.data.jdbc.mapper.FlatEntityMappingPlan.INSTANT;
import static io.micronaut.data.jdbc.mapper.FlatEntityMappingPlan.INT;
import static io.micronaut.data.jdbc.mapper.FlatEntityMappingPlan.LOCAL_DATE;
import static io.micronaut.data.jdbc.mapper.FlatEntityMappingPlan.LOCAL_DATE_TIME;
import static io.micronaut.data.jdbc.mapper.FlatEntityMappingPlan.LOCAL_TIME;
import static io.micronaut.data.jdbc.mapper.FlatEntityMappingPlan.LOCAL_TIME_OF_TIMESTAMP;
import static io.micronaut.data.jdbc.mapper.FlatEntityMappingPlan.LONG;
import static io.micronaut.data.jdbc.mapper.FlatEntityMappingPlan.OFFSET_DATE_TIME;
import static io.micronaut.data.jdbc.mapper.FlatEntityMappingPlan.SHORT;
import static io.micronaut.data.jdbc.mapper.FlatEntityMappingPlan.SQL_DATE;
import static io.micronaut.data.jdbc.mapper.FlatEntityMappingPlan.SQL_TIME;
import static io.micronaut.data.jdbc.mapper.FlatEntityMappingPlan.STRING;
import static io.micronaut.data.jdbc.mapper.FlatEntityMappingPlan.TIMESTAMP;
import static io.micronaut.data.jdbc.mapper.FlatEntityMappingPlan.UUID_VALUE;
import static io.micronaut.data.jdbc.mapper.FlatEntityMappingPlan.ZONED_DATE_TIME;

/**
 * Maps the rows of a {@link ResultSet} onto a flat entity by reading every column with the typed getter matching
 * the property type and setting the property through the compile time bean introspection, with no intermediate
 * boxing, {@link ResultSet#wasNull()} call or conversion.
 *
 * <p>The column ordinals are resolved once from the first row, so an instance belongs to one query execution and
 * is not thread safe, like {@link io.micronaut.data.runtime.mapper.sql.SqlResultEntityTypeMapper}. The plan that
 * decides what to read is shared between executions, see {@link FlatEntityMappingPlan}.</p>
 *
 * <p>{@link ResultSet#wasNull()} is only consulted after a getter that returns a primitive for a property that can
 * hold {@code null}; a primitive property is left at its default for a SQL NULL, which is what the general mapper
 * does as well since it never sets a {@code null} value.</p>
 *
 * @param <R> The entity type
 * @since 5.3.0
 */
@Internal
public final class FlatEntityResultSetMapper<R> implements SqlTypeMapper<ResultSet, R> {

    private final FlatEntityMappingPlan<R> plan;
    private final BeanIntrospection<R> introspection;
    private final BeanProperty<R, Object>[] properties;
    private final RuntimePersistentProperty<R>[] persistentProperties;
    private final byte[] kinds;
    private final int[] constructorArgumentIndexes;
    private final boolean[] requiredConstructorArguments;
    private final int constructorArgumentCount;
    private final ConversionService conversionService;
    private final Map<String, Enum<?>>[] enumsByName;
    private final Enum<?>[][] enumsByOrdinal;
    @Nullable
    private final BiFunction<RuntimePersistentEntity<Object>, Object, Object> loadListener;
    private final Supplier<SqlTypeMapper<ResultSet, R>> generalMapper;
    @Nullable
    private SqlTypeMapper<ResultSet, R> resolvedGeneralMapper;
    /**
     * The ordinals of the plan's columns in {@link #resolvedResultSet}.
     */
    private int[] ordinals = new int[0];
    @Nullable
    private ResultSet resolvedResultSet;
    /**
     * Whether the driver rejected {@link ResultSet#getObject(int, Class)} for a {@code java.time} type, in which case
     * the value is read as the legacy SQL type and converted from there.
     */
    private boolean legacyTimeReads;

    /**
     * @param plan              The plan
     * @param conversionService The conversion service, used only for the values a driver cannot hand back typed
     * @param loadListener      The listener invoked with every mapped entity, for the post load events
     * @param generalMapper     Supplies the general mapper for the operations this mapper does not perform
     */
    public FlatEntityResultSetMapper(FlatEntityMappingPlan<R> plan,
                                     ConversionService conversionService,
                                     @Nullable BiFunction<RuntimePersistentEntity<Object>, Object, Object> loadListener,
                                     Supplier<SqlTypeMapper<ResultSet, R>> generalMapper) {
        this.plan = plan;
        this.introspection = plan.getIntrospection();
        this.properties = plan.getProperties();
        this.persistentProperties = plan.getPersistentProperties();
        this.kinds = plan.getKinds();
        this.constructorArgumentIndexes = plan.getConstructorArgumentIndexes();
        this.requiredConstructorArguments = plan.getRequiredConstructorArguments();
        this.constructorArgumentCount = plan.getConstructorArgumentCount();
        this.conversionService = conversionService;
        this.enumsByName = plan.getEnumsByName();
        this.enumsByOrdinal = plan.getEnumsByOrdinal();
        this.loadListener = loadListener;
        this.generalMapper = generalMapper;
    }

    /**
     * @return The plan
     */
    public FlatEntityMappingPlan<R> getPlan() {
        return plan;
    }

    @Override
    public ConversionService getConversionService() {
        return conversionService;
    }

    @Override
    public boolean hasNext(ResultSet resultSet) {
        try {
            return resultSet.next();
        } catch (SQLException e) {
            throw new DataAccessException("Error calling next on SQL result set: " + e.getMessage(), e);
        }
    }

    @Override
    public R map(ResultSet rs, Class<R> type) throws DataAccessException {
        return readEntity(rs);
    }

    /**
     * Reads the entity of the current row.
     *
     * @param rs The result set
     * @return The entity
     */
    @SuppressWarnings("ReferenceEquality")
    public R readEntity(ResultSet rs) {
        if (rs != resolvedResultSet) {
            resolveOrdinals(rs);
        }
        int[] ordinals = this.ordinals;
        byte[] kinds = this.kinds;
        R entity;
        try {
            if (constructorArgumentCount == 0) {
                entity = introspection.instantiate();
                for (int i = 0; i < kinds.length; i++) {
                    Object value = read(rs, ordinals[i], kinds[i], i);
                    if (value != null) {
                        properties[i].set(entity, value);
                    }
                }
            } else {
                Object[] arguments = new Object[constructorArgumentCount];
                int[] argumentIndexes = this.constructorArgumentIndexes;
                for (int i = 0; i < kinds.length; i++) {
                    int argumentIndex = argumentIndexes[i];
                    if (argumentIndex < 0) {
                        continue;
                    }
                    Object value = read(rs, ordinals[i], kinds[i], i);
                    if (value == null && requiredConstructorArguments[argumentIndex]) {
                        throw new DataAccessException("Null value read for non-null constructor argument [" + persistentProperties[i].getName() + "] of type: " + plan.getEntity().getName());
                    }
                    arguments[argumentIndex] = value;
                }
                entity = introspection.instantiate(arguments);
                for (int i = 0; i < kinds.length; i++) {
                    if (argumentIndexes[i] >= 0) {
                        continue;
                    }
                    Object value = read(rs, ordinals[i], kinds[i], i);
                    if (value != null) {
                        properties[i].set(entity, value);
                    }
                }
            }
        } catch (InstantiationException e) {
            throw new DataAccessException("Error instantiating entity [" + plan.getEntity().getName() + "]: " + e.getMessage(), e);
        }
        if (loadListener != null) {
            entity = (R) loadListener.apply((RuntimePersistentEntity<Object>) plan.getEntity(), entity);
        }
        return entity;
    }

    @Nullable
    private Object read(ResultSet rs, int ordinal, byte kind, int property) {
        try {
            return switch (kind) {
                case STRING -> rs.getString(ordinal);
                case LONG -> rs.getLong(ordinal);
                case BOXED_LONG -> boxedLong(rs, ordinal);
                case INT -> rs.getInt(ordinal);
                case BOXED_INT -> boxedInt(rs, ordinal);
                case SHORT -> rs.getShort(ordinal);
                case BOXED_SHORT -> boxedShort(rs, ordinal);
                case BYTE -> rs.getByte(ordinal);
                case BOXED_BYTE -> boxedByte(rs, ordinal);
                case DOUBLE -> rs.getDouble(ordinal);
                case BOXED_DOUBLE -> boxedDouble(rs, ordinal);
                case FLOAT -> rs.getFloat(ordinal);
                case BOXED_FLOAT -> boxedFloat(rs, ordinal);
                case BOOLEAN -> rs.getBoolean(ordinal);
                case BOXED_BOOLEAN -> boxedBoolean(rs, ordinal);
                case BIG_DECIMAL -> rs.getBigDecimal(ordinal);
                case BIG_INTEGER -> bigInteger(rs, ordinal);
                case BYTES -> rs.getBytes(ordinal);
                case LOCAL_DATE_TIME -> localDateTime(rs, ordinal);
                case LOCAL_DATE -> localDate(rs, ordinal);
                case LOCAL_TIME -> localTime(rs, ordinal);
                case LOCAL_TIME_OF_TIMESTAMP -> localTimeOfTimestamp(rs, ordinal);
                case INSTANT -> instant(rs, ordinal);
                case OFFSET_DATE_TIME -> offsetDateTime(rs, ordinal);
                case ZONED_DATE_TIME -> zonedDateTime(rs, ordinal);
                case TIMESTAMP -> rs.getTimestamp(ordinal);
                case SQL_DATE -> rs.getDate(ordinal);
                case SQL_TIME -> rs.getTime(ordinal);
                case UUID_VALUE -> uuid(rs, ordinal, property);
                case ENUM_NAME -> enumByName(rs, ordinal, property);
                case ENUM_ORDINAL -> enumByOrdinal(rs, ordinal, property);
                default -> throw new IllegalStateException("Unknown read kind " + kind);
            };
        } catch (SQLException e) {
            throw new DataAccessException("Error reading object for name [" + plan.getColumnNames()[property] + "] from result set: " + e.getMessage(), e);
        }
    }

    @Nullable
    private static Long boxedLong(ResultSet rs, int ordinal) throws SQLException {
        long v = rs.getLong(ordinal);
        return v == 0 && rs.wasNull() ? null : v;
    }

    @Nullable
    private static Integer boxedInt(ResultSet rs, int ordinal) throws SQLException {
        int v = rs.getInt(ordinal);
        return v == 0 && rs.wasNull() ? null : v;
    }

    @Nullable
    private static Short boxedShort(ResultSet rs, int ordinal) throws SQLException {
        short v = rs.getShort(ordinal);
        return v == 0 && rs.wasNull() ? null : v;
    }

    @Nullable
    private static Byte boxedByte(ResultSet rs, int ordinal) throws SQLException {
        byte v = rs.getByte(ordinal);
        return v == 0 && rs.wasNull() ? null : v;
    }

    @Nullable
    private static Double boxedDouble(ResultSet rs, int ordinal) throws SQLException {
        double v = rs.getDouble(ordinal);
        return v == 0 && rs.wasNull() ? null : v;
    }

    @Nullable
    private static Float boxedFloat(ResultSet rs, int ordinal) throws SQLException {
        float v = rs.getFloat(ordinal);
        return v == 0 && rs.wasNull() ? null : v;
    }

    @Nullable
    private static Boolean boxedBoolean(ResultSet rs, int ordinal) throws SQLException {
        boolean v = rs.getBoolean(ordinal);
        return (!v && rs.wasNull()) ? null : v;
    }

    @Nullable
    private static BigInteger bigInteger(ResultSet rs, int ordinal) throws SQLException {
        BigDecimal v = rs.getBigDecimal(ordinal);
        return v == null ? null : v.toBigInteger();
    }

    @Nullable
    private LocalDateTime localDateTime(ResultSet rs, int ordinal) throws SQLException {
        if (!legacyTimeReads) {
            try {
                return rs.getObject(ordinal, LocalDateTime.class);
            } catch (SQLException | ClassCastException e) {
                legacyTimeReads = true;
            }
        }
        Timestamp v = rs.getTimestamp(ordinal);
        return v == null ? null : v.toLocalDateTime();
    }

    @Nullable
    private LocalDate localDate(ResultSet rs, int ordinal) throws SQLException {
        if (!legacyTimeReads) {
            try {
                return rs.getObject(ordinal, LocalDate.class);
            } catch (SQLException | ClassCastException e) {
                legacyTimeReads = true;
            }
        }
        java.sql.Date v = rs.getDate(ordinal);
        return v == null ? null : v.toLocalDate();
    }

    @Nullable
    private LocalTime localTime(ResultSet rs, int ordinal) throws SQLException {
        if (!legacyTimeReads) {
            try {
                return rs.getObject(ordinal, LocalTime.class);
            } catch (SQLException | ClassCastException e) {
                legacyTimeReads = true;
            }
        }
        java.sql.Time v = rs.getTime(ordinal);
        return v == null ? null : v.toLocalTime();
    }

    /**
     * The same conversion the conversion service applies to a {@link Timestamp} read for a {@link LocalTime}.
     */
    @Nullable
    private static LocalTime localTimeOfTimestamp(ResultSet rs, int ordinal) throws SQLException {
        Timestamp v = rs.getTimestamp(ordinal);
        return v == null ? null : v.toInstant().atZone(ZoneId.systemDefault()).toLocalTime();
    }

    @Nullable
    private static Instant instant(ResultSet rs, int ordinal) throws SQLException {
        Timestamp v = rs.getTimestamp(ordinal);
        return v == null ? null : v.toInstant();
    }

    /**
     * The same conversion the conversion service applies to a {@link Timestamp}.
     */
    @Nullable
    private static OffsetDateTime offsetDateTime(ResultSet rs, int ordinal) throws SQLException {
        Timestamp v = rs.getTimestamp(ordinal);
        return v == null ? null : v.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    @Nullable
    private static ZonedDateTime zonedDateTime(ResultSet rs, int ordinal) throws SQLException {
        Timestamp v = rs.getTimestamp(ordinal);
        return v == null ? null : v.toInstant().atZone(ZoneId.systemDefault());
    }

    @Nullable
    private Object uuid(ResultSet rs, int ordinal, int property) throws SQLException {
        Object v;
        try {
            v = rs.getObject(ordinal, UUID.class);
        } catch (SQLException | ClassCastException e) {
            v = rs.getObject(ordinal);
        }
        if (v == null || v instanceof UUID) {
            return v;
        }
        return convert(v, property);
    }

    /**
     * An enum stored by name: an exact name is looked up directly, which is the first thing the conversion service
     * tries; anything else goes through the conversion service, so a custom converter or the case-insensitive
     * matching of the default one apply as they do in the general mapper.
     */
    @Nullable
    private Object enumByName(ResultSet rs, int ordinal, int property) throws SQLException {
        String v = rs.getString(ordinal);
        if (v == null) {
            return null;
        }
        Enum<?> constant = enumsByName[property].get(v);
        return constant != null ? constant : convert(v, property);
    }

    /**
     * An enum stored by ordinal, resolved the way the conversion service resolves a number to an enum.
     */
    @Nullable
    private Object enumByOrdinal(ResultSet rs, int ordinal, int property) throws SQLException {
        int v = rs.getInt(ordinal);
        if (v == 0 && rs.wasNull()) {
            return null;
        }
        Enum<?>[] constants = enumsByOrdinal[property];
        return v >= 0 && v < constants.length ? constants[v] : convert(v, property);
    }

    /**
     * Converts a value the typed read could not hand back in the property type, the way the general mapper
     * converts every value whose type doesn't match.
     */
    private Object convert(Object v, int property) {
        Argument<Object> argument = persistentProperties[property].getArgument();
        Object converted = conversionService.convert(v, argument).orElse(null);
        if (converted == null) {
            throw new DataAccessException("Cannot convert type [" + v.getClass() + "] with value [" + v + "] to target type: " + argument + ". Consider defining a TypeConverter bean to handle this case.");
        }
        return converted;
    }

    private void resolveOrdinals(ResultSet rs) {
        String[] columnNames = plan.getColumnNames();
        int[] ordinals = new int[columnNames.length];
        for (int i = 0; i < columnNames.length; i++) {
            try {
                ordinals[i] = rs.findColumn(columnNames[i]);
            } catch (SQLException e) {
                throw new DataAccessException("Error reading object for name [" + columnNames[i] + "] from result set: " + e.getMessage(), e);
            }
        }
        this.ordinals = ordinals;
        this.resolvedResultSet = rs;
    }

    @Nullable
    @Override
    public Object read(ResultSet resultSet, String name) {
        return getGeneralMapper().read(resultSet, name);
    }

    @Nullable
    @Override
    public Object read(ResultSet resultSet, Argument<?> argument) {
        return getGeneralMapper().read(resultSet, argument);
    }

    private SqlTypeMapper<ResultSet, R> getGeneralMapper() {
        if (resolvedGeneralMapper == null) {
            resolvedGeneralMapper = generalMapper.get();
        }
        return resolvedGeneralMapper;
    }
}
