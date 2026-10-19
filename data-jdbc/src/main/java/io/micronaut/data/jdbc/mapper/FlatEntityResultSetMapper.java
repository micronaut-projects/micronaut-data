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
import io.micronaut.data.runtime.convert.DatabaseConversionContextFactory;
import io.micronaut.data.runtime.mapper.ResultReader;
import io.micronaut.data.runtime.mapper.sql.SqlJsonColumnReader;
import io.micronaut.data.runtime.mapper.sql.SqlTypeMapper;
import org.jspecify.annotations.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static io.micronaut.data.jdbc.mapper.FlatEntityMappingPlan.BIG_DECIMAL;
import static io.micronaut.data.jdbc.mapper.FlatEntityMappingPlan.BOOLEAN;
import static io.micronaut.data.jdbc.mapper.FlatEntityMappingPlan.BYTE;
import static io.micronaut.data.jdbc.mapper.FlatEntityMappingPlan.BYTES;
import static io.micronaut.data.jdbc.mapper.FlatEntityMappingPlan.CHARACTER;
import static io.micronaut.data.jdbc.mapper.FlatEntityMappingPlan.DATE;
import static io.micronaut.data.jdbc.mapper.FlatEntityMappingPlan.DOUBLE;
import static io.micronaut.data.jdbc.mapper.FlatEntityMappingPlan.FLOAT;
import static io.micronaut.data.jdbc.mapper.FlatEntityMappingPlan.INTEGER;
import static io.micronaut.data.jdbc.mapper.FlatEntityMappingPlan.LONG;
import static io.micronaut.data.jdbc.mapper.FlatEntityMappingPlan.SHORT;
import static io.micronaut.data.jdbc.mapper.FlatEntityMappingPlan.STRING;
import static io.micronaut.data.jdbc.mapper.FlatEntityMappingPlan.TIME;
import static io.micronaut.data.jdbc.mapper.FlatEntityMappingPlan.TIMESTAMP;
import static io.micronaut.data.jdbc.mapper.FlatEntityMappingPlan.UUID_VALUE;

/**
 * Maps the rows of a {@link ResultSet} onto a flat entity by reading every column with the typed getter of its
 * data type by ordinal and setting the property through the compile time bean introspection.
 *
 * <p>The values are the ones {@link io.micronaut.data.runtime.mapper.sql.SqlResultEntityTypeMapper} produces: a
 * column is read with the getter {@code readDynamic} uses for its data type, a SQL NULL is {@code null} whatever
 * the property type (so a primitive property keeps its initial value, and a non-null constructor argument fails),
 * and a value that is not of the property type goes through the conversion service, including any converter
 * registered for it. What is saved is the per-column dispatch, boxing and mapping context of the general mapper,
 * and the conversion of every property whose type is what its getter returns.</p>
 *
 * <p>When the plan holds a {@link GeneratedRowMapper}, the row is read by that generated class instead, with the
 * ordinals resolved here the same way; the values are the same.</p>
 *
 * <p>The column ordinals are resolved once from the first row, so an instance belongs to one query execution and
 * is not thread safe, like the general mapper. The plan that decides what to read is shared between executions,
 * see {@link FlatEntityMappingPlan}.</p>
 *
 * @param <R> The entity type
 * @since 5.3.0
 */
@Internal
public final class FlatEntityResultSetMapper<R> implements SqlTypeMapper<ResultSet, R> {

    private final FlatEntityMappingPlan<R> plan;
    @Nullable
    private final GeneratedRowMapper<R> generatedMapper;
    @Nullable
    private final GeneratedRowMapperContext generatedContext;
    private final BeanIntrospection<R> introspection;
    private final BeanProperty<R, Object>[] properties;
    private final RuntimePersistentProperty<R>[] persistentProperties;
    private final byte[] kinds;
    private final boolean[] conversions;
    private final int[] constructorArgumentIndexes;
    private final boolean[] requiredConstructorArguments;
    private final int constructorArgumentCount;
    private final boolean embeddable;
    private final ConversionService conversionService;
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
     * @param plan              The plan
     * @param conversionService The conversion service, for the values a getter doesn't return in the property type
     * @param loadListener      The listener invoked with every mapped entity, for the post load events
     * @param generalMapper     Supplies the general mapper for the operations this mapper does not perform
     */
    public FlatEntityResultSetMapper(FlatEntityMappingPlan<R> plan,
                                     ConversionService conversionService,
                                     @Nullable BiFunction<RuntimePersistentEntity<Object>, Object, Object> loadListener,
                                     Supplier<SqlTypeMapper<ResultSet, R>> generalMapper) {
        this(plan, conversionService, loadListener, generalMapper, null);
    }

    /**
     * @param plan                     The plan
     * @param conversionService        The conversion service, for the values a getter doesn't return in the property type
     * @param loadListener             The listener invoked with every mapped entity, for the post load events
     * @param generalMapper            Supplies the general mapper for the operations this mapper does not perform
     * @param conversionContextFactory The factory of the conversion contexts the attribute converters of a generated
     *                                 mapper's columns receive, or {@code null} for plain ones
     */
    public FlatEntityResultSetMapper(FlatEntityMappingPlan<R> plan,
                                     ConversionService conversionService,
                                     @Nullable BiFunction<RuntimePersistentEntity<Object>, Object, Object> loadListener,
                                     Supplier<SqlTypeMapper<ResultSet, R>> generalMapper,
                                     @Nullable DatabaseConversionContextFactory conversionContextFactory) {
        this(plan, conversionService, loadListener, generalMapper, conversionContextFactory, null, null);
    }

    /**
     * @param plan                     The plan
     * @param conversionService        The conversion service, for the values a getter doesn't return in the property type
     * @param loadListener             The listener invoked with every mapped entity, for the post load events
     * @param generalMapper            Supplies the general mapper for the operations this mapper does not perform
     * @param conversionContextFactory The factory of the conversion contexts the attribute converters of a generated
     *                                 mapper's columns receive, or {@code null} for plain ones
     * @param jsonColumnReader         The JSON column reader of the query for the JSON columns of a generated mapper, or
     *                                 {@code null} to read them as strings
     * @param resultReader             The result reader the JSON column reader reads with
     */
    public FlatEntityResultSetMapper(FlatEntityMappingPlan<R> plan,
                                     ConversionService conversionService,
                                     @Nullable BiFunction<RuntimePersistentEntity<Object>, Object, Object> loadListener,
                                     Supplier<SqlTypeMapper<ResultSet, R>> generalMapper,
                                     @Nullable DatabaseConversionContextFactory conversionContextFactory,
                                     @Nullable SqlJsonColumnReader<ResultSet> jsonColumnReader,
                                     @Nullable ResultReader<ResultSet, String> resultReader) {
        this.plan = plan;
        this.generatedMapper = plan.getGeneratedMapper();
        this.generatedContext = generatedMapper == null ? null : plan.createContext(conversionService, conversionContextFactory, jsonColumnReader, resultReader);
        this.introspection = plan.getIntrospection();
        this.properties = plan.getProperties();
        this.persistentProperties = plan.getPersistentProperties();
        this.kinds = plan.getKinds();
        this.conversions = plan.getConversions();
        this.constructorArgumentIndexes = plan.getConstructorArgumentIndexes();
        this.requiredConstructorArguments = plan.getRequiredConstructorArguments();
        this.constructorArgumentCount = plan.getConstructorArgumentCount();
        this.embeddable = plan.isEmbeddable();
        this.conversionService = conversionService;
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

    @Nullable
    @Override
    public R map(ResultSet rs, Class<R> type) throws DataAccessException {
        return readEntity(rs);
    }

    /**
     * Reads the entity of the current row.
     *
     * @param rs The result set
     * @return The entity, or {@code null} for an embeddable whose required constructor argument is NULL
     */
    @Nullable
    @SuppressWarnings("ReferenceEquality")
    public R readEntity(ResultSet rs) {
        if (rs != resolvedResultSet) {
            resolveOrdinals(rs);
        }
        R entity;
        if (generatedMapper != null) {
            try {
                entity = (R) generatedMapper.map(rs, ordinals, Objects.requireNonNull(generatedContext));
            } catch (SQLException e) {
                throw new DataAccessException("Error reading entity [" + plan.getEntity().getName() + "] from result set: " + e.getMessage(), e);
            }
        } else {
            try {
                entity = constructorArgumentCount == 0 ? introspection.instantiate() : instantiate(rs);
            } catch (InstantiationException e) {
                throw new DataAccessException("Error instantiating entity [" + plan.getEntity().getName() + "]: " + e.getMessage(), e);
            }
            if (entity == null) {
                return null;
            }
            for (int i = 0; i < kinds.length; i++) {
                if (constructorArgumentIndexes[i] >= 0) {
                    continue;
                }
                Object value = read(rs, ordinals[i], kinds[i], i);
                if (value != null) {
                    properties[i].set(entity, value);
                }
            }
        }
        if (loadListener != null) {
            entity = (R) loadListener.apply((RuntimePersistentEntity<Object>) plan.getEntity(), entity);
        }
        return entity;
    }

    /**
     * Instantiates the entity with the columns of its constructor arguments. A required argument that is NULL is
     * an error, except for an embeddable, which the general mapper reads as {@code null} then (an embedded
     * projection of a row without the value).
     */
    @Nullable
    private R instantiate(ResultSet rs) {
        Object[] arguments = new Object[constructorArgumentCount];
        for (int i = 0; i < kinds.length; i++) {
            int argumentIndex = constructorArgumentIndexes[i];
            if (argumentIndex < 0) {
                continue;
            }
            Object value = read(rs, ordinals[i], kinds[i], i);
            if (value == null && requiredConstructorArguments[argumentIndex]) {
                if (embeddable) {
                    return null;
                }
                throw new DataAccessException("Null value read for non-null constructor argument [" + persistentProperties[i].getName() + "] of type: " + plan.getEntity().getName());
            }
            arguments[argumentIndex] = value;
        }
        return introspection.instantiate(arguments);
    }

    /**
     * Reads a column with the typed getter of its kind. A SQL NULL is {@code null}: the getters returning a
     * primitive are followed by {@link ResultSet#wasNull()} when they return zero, which is when the column can be
     * NULL. The value is converted when the plan found the getter's type is not the property type.
     */
    @Nullable
    private Object read(ResultSet rs, int ordinal, byte kind, int property) {
        Object value;
        try {
            value = switch (kind) {
                case STRING -> rs.getString(ordinal);
                case LONG -> readLong(rs, ordinal);
                case INTEGER -> readInt(rs, ordinal);
                case SHORT -> readShort(rs, ordinal);
                case BYTE -> readByte(rs, ordinal);
                case DOUBLE -> readDouble(rs, ordinal);
                case FLOAT -> readFloat(rs, ordinal);
                case BOOLEAN -> readBoolean(rs, ordinal);
                case BIG_DECIMAL -> rs.getBigDecimal(ordinal);
                case BYTES -> rs.getBytes(ordinal);
                case TIMESTAMP -> rs.getTimestamp(ordinal);
                case DATE -> rs.getDate(ordinal);
                case TIME -> rs.getTime(ordinal);
                case CHARACTER -> readChar(rs, ordinal);
                case UUID_VALUE -> readUuid(rs, ordinal, property);
                default -> throw new IllegalStateException("Unknown read kind " + kind);
            };
        } catch (SQLException e) {
            throw new DataAccessException("Error reading object for name [" + plan.getColumnNames()[property] + "] from result set: " + e.getMessage(), e);
        }
        if (value != null && conversions[property]) {
            return convert(value, property);
        }
        return value;
    }

    @Nullable
    private static Long readLong(ResultSet rs, int ordinal) throws SQLException {
        long v = rs.getLong(ordinal);
        return v == 0 && rs.wasNull() ? null : v;
    }

    @Nullable
    private static Integer readInt(ResultSet rs, int ordinal) throws SQLException {
        int v = rs.getInt(ordinal);
        return v == 0 && rs.wasNull() ? null : v;
    }

    @Nullable
    private static Short readShort(ResultSet rs, int ordinal) throws SQLException {
        short v = rs.getShort(ordinal);
        return v == 0 && rs.wasNull() ? null : v;
    }

    @Nullable
    private static Byte readByte(ResultSet rs, int ordinal) throws SQLException {
        byte v = rs.getByte(ordinal);
        return v == 0 && rs.wasNull() ? null : v;
    }

    @Nullable
    private static Double readDouble(ResultSet rs, int ordinal) throws SQLException {
        double v = rs.getDouble(ordinal);
        return v == 0 && rs.wasNull() ? null : v;
    }

    @Nullable
    private static Float readFloat(ResultSet rs, int ordinal) throws SQLException {
        float v = rs.getFloat(ordinal);
        return v == 0 && rs.wasNull() ? null : v;
    }

    @Nullable
    private static Boolean readBoolean(ResultSet rs, int ordinal) throws SQLException {
        boolean v = rs.getBoolean(ordinal);
        return (!v && rs.wasNull()) ? null : v;
    }

    /**
     * The first character of the column, or the zero character of an empty string, the way
     * {@link ColumnIndexResultSetReader#readChar} reads it.
     */
    @Nullable
    private static Character readChar(ResultSet rs, int ordinal) throws SQLException {
        String v = rs.getString(ordinal);
        if (v == null) {
            return null;
        }
        return v.isEmpty() ? (char) 0 : v.charAt(0);
    }

    /**
     * A UUID the way {@link ColumnIndexResultSetReader#getRequiredValue} reads one: through
     * {@link ResultSet#getObject(int, Class)}, or {@link ResultSet#getObject(int)} and a conversion when the driver
     * cannot return the type.
     */
    @Nullable
    private Object readUuid(ResultSet rs, int ordinal, int property) throws SQLException {
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
     * Converts a value the getter did not return in the property type, the way the general mapper converts every
     * value whose type doesn't match, through the conversion service and whatever converters are registered with it.
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
        int[] resolved = new int[columnNames.length];
        for (int i = 0; i < columnNames.length; i++) {
            try {
                resolved[i] = rs.findColumn(columnNames[i]);
            } catch (SQLException e) {
                throw new DataAccessException("Error reading object for name [" + columnNames[i] + "] from result set: " + e.getMessage(), e);
            }
        }
        this.ordinals = resolved;
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
