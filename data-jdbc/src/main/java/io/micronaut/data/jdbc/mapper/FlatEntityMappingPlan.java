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
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * How {@link FlatEntityResultSetMapper} reads one entity type: the column of every mapped property, the typed
 * {@link java.sql.ResultSet} getter of the property's {@link DataType} and whether the value it returns has to be
 * converted to the property type, resolved once per entity type.
 *
 * <p>The getter of a column is the one {@link io.micronaut.data.runtime.mapper.ResultReader#readDynamic} picks for
 * the data type, and a value whose class is not the property type is converted through the conversion service, so
 * a flat entity is read to the same values as by
 * {@link io.micronaut.data.runtime.mapper.sql.SqlResultEntityTypeMapper}; what the plan saves is the per-column
 * dispatch, boxing and mapping context of that mapper, and the conversion for every property whose type is what the
 * getter returns.</p>
 *
 * <p>A plan exists only for a flat entity: no associations or embedded values, no JSON columns, no attribute
 * converters, no composite identity, and only data types with a typed getter. {@link #of} returns {@code null}
 * for any other entity, which is then mapped by the general mapper as before.</p>
 *
 * @param <R> The entity type
 * @since 5.3.0
 */
@Internal
public final class FlatEntityMappingPlan<R> {

    static final byte STRING = 1;
    static final byte LONG = 2;
    static final byte INTEGER = 3;
    static final byte SHORT = 4;
    static final byte BYTE = 5;
    static final byte DOUBLE = 6;
    static final byte FLOAT = 7;
    static final byte BOOLEAN = 8;
    static final byte BIG_DECIMAL = 9;
    static final byte BYTES = 10;
    static final byte TIMESTAMP = 11;
    static final byte DATE = 12;
    static final byte TIME = 13;
    static final byte UUID_VALUE = 14;
    static final byte CHARACTER = 15;

    private final RuntimePersistentEntity<R> entity;
    private final BeanIntrospection<R> introspection;
    private final RuntimePersistentProperty<R>[] persistentProperties;
    private final BeanProperty<R, Object>[] properties;
    private final String[] columnNames;
    private final byte[] kinds;
    /**
     * Whether the value the getter returns is not of the property type and has to be converted, which is decided
     * once here for every kind but {@link #UUID_VALUE}, whose read can return a driver specific type.
     */
    private final boolean[] conversions;
    /**
     * The constructor argument each mapped property supplies, or {@code -1} when the property is set after
     * instantiation.
     */
    private final int[] constructorArgumentIndexes;
    private final boolean[] requiredConstructorArguments;
    private final int constructorArgumentCount;

    private FlatEntityMappingPlan(RuntimePersistentEntity<R> entity,
                                  List<RuntimePersistentProperty<R>> mapped,
                                  List<String> columnNames,
                                  byte[] kinds,
                                  boolean[] conversions,
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
        this.conversions = conversions;
        this.constructorArgumentIndexes = constructorArgumentIndexes;
        this.requiredConstructorArguments = requiredConstructorArguments;
        this.constructorArgumentCount = constructorArgumentCount;
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
        List<Boolean> conversions = new ArrayList<>(candidates.size());
        List<Integer> argumentIndexes = new ArrayList<>(candidates.size());
        // Every constructor argument is a persistent property, the identity or the version, so each is among the
        // candidates and is either mapped or makes the entity fall back
        boolean[] requiredArguments = new boolean[constructorArguments.length];
        for (RuntimePersistentProperty<R> property : candidates) {
            if (property instanceof RuntimeAssociation || property.getConverter() != null) {
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
                requiredArguments[argumentIndex] = !property.isOptional();
            }
            byte kind = kind(property.getDataType());
            if (kind == 0) {
                return null;
            }
            mapped.add(property);
            columnNames.add(columnName(namingStrategy, property));
            kinds.add(kind);
            conversions.add(!property.getType().isAssignableFrom(getterType(kind)));
            argumentIndexes.add(argumentIndex);
        }
        byte[] kindArray = new byte[kinds.size()];
        boolean[] conversionArray = new boolean[kinds.size()];
        int[] argumentIndexArray = new int[kinds.size()];
        for (int i = 0; i < kindArray.length; i++) {
            kindArray[i] = kinds.get(i);
            conversionArray[i] = conversions.get(i);
            argumentIndexArray[i] = argumentIndexes.get(i);
        }
        return new FlatEntityMappingPlan<>(entity, mapped, columnNames, kindArray, conversionArray, argumentIndexArray, requiredArguments, constructorArguments.length);
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
     * @return The read kind of a data type, the typed getter {@code readDynamic} uses for it, or {@code 0} for a
     * data type that is read through {@code getObject}
     */
    private static byte kind(DataType dataType) {
        return switch (dataType) {
            case STRING -> STRING;
            case LONG -> LONG;
            case INTEGER -> INTEGER;
            case SHORT -> SHORT;
            case BYTE -> BYTE;
            case DOUBLE -> DOUBLE;
            case FLOAT -> FLOAT;
            case BOOLEAN -> BOOLEAN;
            case BIGDECIMAL -> BIG_DECIMAL;
            case BYTE_ARRAY -> BYTES;
            case TIMESTAMP -> TIMESTAMP;
            case DATE -> DATE;
            case TIME -> TIME;
            case UUID -> UUID_VALUE;
            case CHARACTER -> CHARACTER;
            default -> 0;
        };
    }

    /**
     * @return The class of the value a kind's getter returns
     */
    private static Class<?> getterType(byte kind) {
        return switch (kind) {
            case STRING -> String.class;
            case LONG -> Long.class;
            case INTEGER -> Integer.class;
            case SHORT -> Short.class;
            case BYTE -> Byte.class;
            case DOUBLE -> Double.class;
            case FLOAT -> Float.class;
            case BOOLEAN -> Boolean.class;
            case BIG_DECIMAL -> BigDecimal.class;
            case BYTES -> byte[].class;
            case TIMESTAMP -> Timestamp.class;
            case DATE -> java.sql.Date.class;
            case TIME -> java.sql.Time.class;
            case UUID_VALUE -> UUID.class;
            case CHARACTER -> Character.class;
            default -> throw new IllegalStateException("Unknown read kind " + kind);
        };
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

    boolean[] getConversions() {
        return conversions;
    }

    int[] getConstructorArgumentIndexes() {
        return constructorArgumentIndexes;
    }

    boolean[] getRequiredConstructorArguments() {
        return requiredConstructorArguments;
    }

    int getConstructorArgumentCount() {
        return constructorArgumentCount;
    }
}
