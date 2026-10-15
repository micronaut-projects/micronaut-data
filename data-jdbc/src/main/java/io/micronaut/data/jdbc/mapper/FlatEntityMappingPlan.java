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
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.naming.NamingStrategy;
import io.micronaut.data.model.runtime.RuntimeAssociation;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * How {@link FlatEntityResultSetMapper} reads one entity type: the column of every mapped property and the typed
 * {@link java.sql.ResultSet} getter matching the property type, resolved once per entity type.
 *
 * <p>A plan exists only for a flat entity, one whose columns map one to one onto properties that the driver can hand
 * back in the property's own type: no associations or embedded values, no JSON columns, no attribute converters and
 * no composite identity. {@link #of} returns {@code null} for any other entity, which is then mapped by
 * {@link io.micronaut.data.runtime.mapper.sql.SqlResultEntityTypeMapper} as before.</p>
 *
 * @param <R> The entity type
 * @since 5.3.0
 */
@Internal
public final class FlatEntityMappingPlan<R> {

    static final byte STRING = 1;
    static final byte LONG = 2;
    static final byte BOXED_LONG = 3;
    static final byte INT = 4;
    static final byte BOXED_INT = 5;
    static final byte SHORT = 6;
    static final byte BOXED_SHORT = 7;
    static final byte BYTE = 8;
    static final byte BOXED_BYTE = 9;
    static final byte DOUBLE = 10;
    static final byte BOXED_DOUBLE = 11;
    static final byte FLOAT = 12;
    static final byte BOXED_FLOAT = 13;
    static final byte BOOLEAN = 14;
    static final byte BOXED_BOOLEAN = 15;
    static final byte BIG_DECIMAL = 16;
    static final byte BIG_INTEGER = 17;
    static final byte BYTES = 18;
    static final byte LOCAL_DATE_TIME = 19;
    static final byte LOCAL_DATE = 20;
    static final byte LOCAL_TIME = 21;
    static final byte INSTANT = 22;
    static final byte OFFSET_DATE_TIME = 23;
    static final byte ZONED_DATE_TIME = 24;
    static final byte TIMESTAMP = 25;
    static final byte SQL_DATE = 26;
    static final byte SQL_TIME = 27;
    static final byte UUID_VALUE = 28;
    static final byte LOCAL_TIME_OF_TIMESTAMP = 29;
    static final byte ENUM_NAME = 30;
    static final byte ENUM_ORDINAL = 31;

    private final RuntimePersistentEntity<R> entity;
    private final BeanIntrospection<R> introspection;
    private final RuntimePersistentProperty<R>[] persistentProperties;
    private final BeanProperty<R, Object>[] properties;
    private final String[] columnNames;
    private final byte[] kinds;
    /**
     * The constructor argument each mapped property supplies, or {@code -1} when the property is set after
     * instantiation.
     */
    private final int[] constructorArgumentIndexes;
    private final boolean[] requiredConstructorArguments;
    private final int constructorArgumentCount;
    /**
     * For an {@link #ENUM_NAME} property, its constants by name; for an {@link #ENUM_ORDINAL} property, its
     * constants in ordinal order. The same lookups the conversion service performs for an exact name or ordinal.
     */
    private final Map<String, Enum<?>>[] enumsByName;
    private final Enum<?>[][] enumsByOrdinal;

    private FlatEntityMappingPlan(RuntimePersistentEntity<R> entity,
                                  List<RuntimePersistentProperty<R>> mapped,
                                  List<String> columnNames,
                                  byte[] kinds,
                                  int[] constructorArgumentIndexes,
                                  boolean[] requiredConstructorArguments,
                                  int constructorArgumentCount) {
        this.entity = entity;
        this.introspection = entity.getIntrospection();
        this.persistentProperties = mapped.toArray(new RuntimePersistentProperty[0]);
        this.properties = new BeanProperty[mapped.size()];
        for (int i = 0; i < persistentProperties.length; i++) {
            properties[i] = persistentProperties[i].getProperty();
        }
        this.columnNames = columnNames.toArray(new String[0]);
        this.kinds = kinds;
        this.constructorArgumentIndexes = constructorArgumentIndexes;
        this.requiredConstructorArguments = requiredConstructorArguments;
        this.constructorArgumentCount = constructorArgumentCount;
        this.enumsByName = new Map[kinds.length];
        this.enumsByOrdinal = new Enum[kinds.length][];
        for (int i = 0; i < kinds.length; i++) {
            if (kinds[i] == ENUM_NAME || kinds[i] == ENUM_ORDINAL) {
                Enum<?>[] constants = (Enum<?>[]) persistentProperties[i].getType().getEnumConstants();
                enumsByOrdinal[i] = constants;
                Map<String, Enum<?>> byName = new HashMap<>(constants.length * 2);
                for (Enum<?> constant : constants) {
                    byName.put(constant.name(), constant);
                }
                enumsByName[i] = byName;
            }
        }
    }

    /**
     * Resolves the plan of the given entity.
     *
     * @param entity The entity
     * @param <R>    The entity type
     * @return The plan, or {@code null} when the entity is not flat and has to be mapped by the general mapper
     */
    @Nullable
    public static <R> FlatEntityMappingPlan<R> of(RuntimePersistentEntity<R> entity) {
        if (!entity.hasIdentity() && !entity.getIdentityProperties().isEmpty()) {
            // A composite identity is read as a map of its values
            return null;
        }
        List<RuntimePersistentProperty<R>> candidates = new ArrayList<>();
        if (entity.hasIdentity()) {
            candidates.add(entity.getIdentity());
        }
        if (entity.hasVersion()) {
            candidates.add(entity.getVersion());
        }
        for (RuntimePersistentProperty<R> property : entity.getPersistentProperties()) {
            if (!candidates.contains(property)) {
                candidates.add(property);
            }
        }
        RuntimePersistentProperty<R>[] constructorArguments = entity.getConstructorArguments();
        NamingStrategy namingStrategy = entity.getNamingStrategy();

        List<RuntimePersistentProperty<R>> mapped = new ArrayList<>(candidates.size());
        List<String> columnNames = new ArrayList<>(candidates.size());
        List<Byte> kinds = new ArrayList<>(candidates.size());
        List<Integer> argumentIndexes = new ArrayList<>(candidates.size());
        boolean[] requiredArguments = new boolean[constructorArguments.length];
        boolean[] suppliedArguments = new boolean[constructorArguments.length];
        for (RuntimePersistentProperty<R> property : candidates) {
            if (property instanceof RuntimeAssociation || property.getConverter() != null
                || property.getDataType() == DataType.JSON) {
                return null;
            }
            int argumentIndex = indexOf(constructorArguments, property);
            if (argumentIndex < 0) {
                if (property.isReadOnly() || property.isConstructorArgument()) {
                    if ((entity.hasIdentity() && property == entity.getIdentity()) || (entity.hasVersion() && property == entity.getVersion())) {
                        // The general mapper sets these through a copy of the entity; not worth replicating here
                        return null;
                    }
                    // The general mapper doesn't set such a property either
                    continue;
                }
            } else {
                suppliedArguments[argumentIndex] = true;
                requiredArguments[argumentIndex] = !property.isOptional();
            }
            byte kind = kind(property);
            if (kind == 0) {
                return null;
            }
            mapped.add(property);
            columnNames.add(columnName(namingStrategy, property));
            kinds.add(kind);
            argumentIndexes.add(argumentIndex);
        }
        for (boolean supplied : suppliedArguments) {
            if (!supplied) {
                // A constructor argument that is not a persistent property, such as an association
                return null;
            }
        }
        byte[] kindArray = new byte[kinds.size()];
        int[] argumentIndexArray = new int[argumentIndexes.size()];
        for (int i = 0; i < kindArray.length; i++) {
            kindArray[i] = kinds.get(i);
            argumentIndexArray[i] = argumentIndexes.get(i);
        }
        return new FlatEntityMappingPlan<>(entity, mapped, columnNames, kindArray, argumentIndexArray, requiredArguments, constructorArguments.length);
    }

    private static <R> int indexOf(RuntimePersistentProperty<R>[] constructorArguments, RuntimePersistentProperty<R> property) {
        for (int i = 0; i < constructorArguments.length; i++) {
            if (constructorArguments[i] == property || constructorArguments[i].getName().equals(property.getName())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The column of a property of the root entity, resolved the way the general mapper resolves it for an entity
     * read without a prefix.
     */
    private static String columnName(NamingStrategy namingStrategy, RuntimePersistentProperty<?> property) {
        String alias = property.getAlias();
        if (StringUtils.isNotEmpty(alias)) {
            return alias;
        }
        return namingStrategy.mappedName(Collections.emptyList(), property);
    }

    /**
     * @return The read kind of the property, or {@code 0} when the driver cannot hand the value back in the property
     * type or the column's data type is not the natural one of that type
     */
    private static byte kind(RuntimePersistentProperty<?> property) {
        Class<?> type = property.getProperty().getType();
        DataType dataType = property.getDataType();
        if (type == String.class) {
            return dataType == DataType.STRING ? STRING : 0;
        }
        if (type == long.class || type == Long.class) {
            return dataType == DataType.LONG ? (type.isPrimitive() ? LONG : BOXED_LONG) : 0;
        }
        if (type == int.class || type == Integer.class) {
            return dataType == DataType.INTEGER ? (type.isPrimitive() ? INT : BOXED_INT) : 0;
        }
        if (type == short.class || type == Short.class) {
            return dataType == DataType.SHORT ? (type.isPrimitive() ? SHORT : BOXED_SHORT) : 0;
        }
        if (type == byte.class || type == Byte.class) {
            return dataType == DataType.BYTE ? (type.isPrimitive() ? BYTE : BOXED_BYTE) : 0;
        }
        if (type == double.class || type == Double.class) {
            return dataType == DataType.DOUBLE ? (type.isPrimitive() ? DOUBLE : BOXED_DOUBLE) : 0;
        }
        if (type == float.class || type == Float.class) {
            return dataType == DataType.FLOAT ? (type.isPrimitive() ? FLOAT : BOXED_FLOAT) : 0;
        }
        if (type == boolean.class || type == Boolean.class) {
            return dataType == DataType.BOOLEAN ? (type.isPrimitive() ? BOOLEAN : BOXED_BOOLEAN) : 0;
        }
        if (type == BigDecimal.class) {
            return dataType == DataType.BIGDECIMAL ? BIG_DECIMAL : 0;
        }
        if (type == BigInteger.class) {
            return dataType == DataType.BIGDECIMAL ? BIG_INTEGER : 0;
        }
        if (type == byte[].class) {
            return dataType == DataType.BYTE_ARRAY ? BYTES : 0;
        }
        if (type == LocalDateTime.class) {
            return dataType == DataType.TIMESTAMP ? LOCAL_DATE_TIME : 0;
        }
        if (type == LocalDate.class) {
            return dataType == DataType.DATE ? LOCAL_DATE : 0;
        }
        if (type == LocalTime.class) {
            // The annotation processor types a LocalTime column as a TIMESTAMP
            return dataType == DataType.TIME ? LOCAL_TIME : dataType == DataType.TIMESTAMP ? LOCAL_TIME_OF_TIMESTAMP : 0;
        }
        if (type == Instant.class) {
            return dataType == DataType.TIMESTAMP ? INSTANT : 0;
        }
        if (type == OffsetDateTime.class) {
            return dataType == DataType.TIMESTAMP ? OFFSET_DATE_TIME : 0;
        }
        if (type == ZonedDateTime.class) {
            return dataType == DataType.TIMESTAMP ? ZONED_DATE_TIME : 0;
        }
        if (type == Timestamp.class) {
            return dataType == DataType.TIMESTAMP ? TIMESTAMP : 0;
        }
        if (type == Date.class) {
            // A java.util.Date is handed the Timestamp or java.sql.Date of its column, both of which are Dates, the
            // way the general mapper does
            return dataType == DataType.TIMESTAMP ? TIMESTAMP : dataType == DataType.DATE ? SQL_DATE : 0;
        }
        if (type == java.sql.Date.class) {
            return dataType == DataType.DATE ? SQL_DATE : 0;
        }
        if (type == java.sql.Time.class) {
            return dataType == DataType.TIME ? SQL_TIME : 0;
        }
        if (type.isEnum()) {
            // Stored by name, or by ordinal when the data type says so, the two cases the conversion service covers
            return dataType == DataType.STRING ? ENUM_NAME : dataType == DataType.INTEGER ? ENUM_ORDINAL : 0;
        }
        if (type == UUID.class) {
            return dataType == DataType.UUID ? UUID_VALUE : 0;
        }
        return 0;
    }

    /**
     * @return The entity
     */
    public RuntimePersistentEntity<R> getEntity() {
        return entity;
    }

    BeanIntrospection<R> getIntrospection() {
        return introspection;
    }

    RuntimePersistentProperty<R>[] getPersistentProperties() {
        return persistentProperties;
    }

    BeanProperty<R, Object>[] getProperties() {
        return properties;
    }

    /**
     * @return The column of every mapped property, in the order of {@link #getProperties()}
     */
    public String[] getColumnNames() {
        return columnNames;
    }

    byte[] getKinds() {
        return kinds;
    }

    int[] getConstructorArgumentIndexes() {
        return constructorArgumentIndexes;
    }

    boolean[] getRequiredConstructorArguments() {
        return requiredConstructorArguments;
    }

    Map<String, Enum<?>>[] getEnumsByName() {
        return enumsByName;
    }

    Enum<?>[][] getEnumsByOrdinal() {
        return enumsByOrdinal;
    }

    int getConstructorArgumentCount() {
        return constructorArgumentCount;
    }
}
