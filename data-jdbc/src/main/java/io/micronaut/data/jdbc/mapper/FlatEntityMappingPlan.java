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
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.EmbeddedId;
import io.micronaut.data.annotation.FlatEntity;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.naming.NamingStrategy;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import io.micronaut.data.runtime.convert.DatabaseConversionContextFactory;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
 * <p>Which entities are flat is decided by the annotation processor, which marks them with {@link FlatEntity}:
 * no associations or embedded values, no JSON columns, no attribute converters, no composite identity, and only
 * data types with a typed getter. Any other entity is mapped by the general mapper as before.</p>
 *
 * <p>When the annotation processor generated a {@link GeneratedRowMapper} for the entity, the plan holds it
 * instead and {@link FlatEntityResultSetMapper} delegates the row read to it; the plan then only resolves the
 * ordinals of the generated mapper's columns. A generated mapper covers what the flat mapper covers and also
 * embedded values and single ended associations by their foreign key, and its values are those the flat mapper
 * reads.</p>
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

    /**
     * A property the general mapper neither reads nor sets, which the plan leaves out as well.
     */
    private static final byte SKIPPED = 0;

    private final RuntimePersistentEntity<R> entity;
    @Nullable
    private final GeneratedRowMapper<R> generatedMapper;
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
    /**
     * Whether the entity is an embeddable, which is null rather than an error when a required constructor
     * argument is NULL, as it is for the general mapper.
     */
    private final boolean embeddable;

    private FlatEntityMappingPlan(RuntimePersistentEntity<R> entity, GeneratedRowMapper<R> generatedMapper) {
        this.entity = entity;
        this.generatedMapper = generatedMapper;
        this.introspection = entity.getIntrospection();
        this.persistentProperties = new RuntimePersistentProperty[0];
        this.properties = new BeanProperty[0];
        this.columnNames = generatedMapper.columnNames();
        this.kinds = new byte[0];
        this.conversions = new boolean[0];
        this.constructorArgumentIndexes = new int[0];
        this.requiredConstructorArguments = new boolean[0];
        this.constructorArgumentCount = 0;
        this.embeddable = false;
    }

    private FlatEntityMappingPlan(RuntimePersistentEntity<R> entity, List<Column<R>> columns, boolean[] requiredConstructorArguments) {
        this.entity = entity;
        this.generatedMapper = null;
        this.introspection = entity.getIntrospection();
        int size = columns.size();
        this.persistentProperties = new RuntimePersistentProperty[size];
        this.properties = new BeanProperty[size];
        this.columnNames = new String[size];
        this.kinds = new byte[size];
        this.conversions = new boolean[size];
        this.constructorArgumentIndexes = new int[size];
        for (int i = 0; i < size; i++) {
            Column<R> column = columns.get(i);
            persistentProperties[i] = column.property;
            properties[i] = column.property.getProperty();
            columnNames[i] = column.name;
            kinds[i] = column.kind;
            conversions[i] = column.conversion;
            constructorArgumentIndexes[i] = column.argumentIndex;
        }
        this.requiredConstructorArguments = requiredConstructorArguments;
        this.constructorArgumentCount = requiredConstructorArguments.length;
        this.embeddable = entity.getAnnotationMetadata().hasAnnotation(Embeddable.class)
            || entity.getAnnotationMetadata().hasAnnotation(EmbeddedId.class);
    }

    /**
     * Resolves the plan of an entity the annotation processor marked with {@link FlatEntity}, which guarantees
     * every property has a typed getter; the processor decides what is flat, this only lays the columns out.
     *
     * @param entity The entity
     * @param <R>    The entity type
     * @return The plan
     * @throws IllegalArgumentException if the entity is not marked as flat
     */
    public static <R> FlatEntityMappingPlan<R> of(RuntimePersistentEntity<R> entity) {
        return of(entity, true);
    }

    /**
     * Resolves the plan of an entity: the one holding the mapper the annotation processor generated for it when
     * there is one and {@code generated} allows it, else the columns of an entity marked with {@link FlatEntity}.
     *
     * @param entity    The entity
     * @param generated Whether to use the mapper the annotation processor generated for the entity when there is one
     * @param <R>       The entity type
     * @return The plan
     * @throws IllegalArgumentException if the entity has no generated mapper to use and is not marked as flat
     */
    public static <R> FlatEntityMappingPlan<R> of(RuntimePersistentEntity<R> entity, boolean generated) {
        if (generated) {
            GeneratedRowMapper<R> generatedMapper = GeneratedRowMappers.find(entity.getIntrospection().getBeanType());
            if (generatedMapper != null) {
                return new FlatEntityMappingPlan<>(entity, generatedMapper);
            }
        }
        if (!isFlat(entity)) {
            throw new IllegalArgumentException("Entity [" + entity.getName() + "] is not a flat entity");
        }
        // Every constructor argument is a persistent property, the identity or the version, so each is among the
        // candidates
        RuntimePersistentProperty<R>[] constructorArguments = entity.getConstructorArguments();
        boolean[] requiredArguments = new boolean[constructorArguments.length];
        List<Column<R>> columns = new ArrayList<>();
        for (RuntimePersistentProperty<R> property : candidates(entity)) {
            Column<R> column = column(entity, property, constructorArguments);
            if (column.kind == SKIPPED) {
                continue;
            }
            if (column.argumentIndex >= 0) {
                requiredArguments[column.argumentIndex] = !property.isOptional();
            }
            columns.add(column);
        }
        return new FlatEntityMappingPlan<>(entity, columns, requiredArguments);
    }

    /**
     * @param entity The entity
     * @return Whether the annotation processor marked the entity as flat, see {@link FlatEntity}
     */
    public static boolean isFlat(RuntimePersistentEntity<?> entity) {
        return entity.getAnnotationMetadata().hasAnnotation(FlatEntity.class);
    }

    /**
     * @param entity The entity
     * @return Whether a plan exists for the entity: the annotation processor generated a row mapper for it or
     * marked it as flat
     */
    public static boolean supports(RuntimePersistentEntity<?> entity) {
        return isFlat(entity) || GeneratedRowMappers.find(entity.getIntrospection().getBeanType()) != null;
    }

    /**
     * @return The identity, the version and the persistent properties of the entity, each once, in that order
     */
    private static <R> Set<RuntimePersistentProperty<R>> candidates(RuntimePersistentEntity<R> entity) {
        Set<RuntimePersistentProperty<R>> candidates = new LinkedHashSet<>();
        if (entity.hasIdentity()) {
            candidates.add(entity.getIdentity());
        }
        if (entity.hasVersion()) {
            candidates.add(entity.getVersion());
        }
        candidates.addAll(entity.getPersistentProperties());
        return candidates;
    }

    /**
     * @return How the property is read, or a {@link #SKIPPED} column for a property that cannot be set and is not
     * a constructor argument, which the general mapper doesn't set either
     */
    private static <R> Column<R> column(RuntimePersistentEntity<R> entity,
                                        RuntimePersistentProperty<R> property,
                                        RuntimePersistentProperty<R>[] constructorArguments) {
        int argumentIndex = indexOf(constructorArguments, property);
        if (argumentIndex < 0 && property.isReadOnly()) {
            return new Column<>(property, "", SKIPPED, false, -1);
        }
        byte kind = kind(property.getDataType());
        if (kind == SKIPPED) {
            throw new IllegalStateException("Property [" + property.getName() + "] of flat entity [" + entity.getName() + "] has no typed getter for " + property.getDataType());
        }
        boolean conversion = !property.getType().isAssignableFrom(getterType(kind));
        return new Column<>(property, columnName(entity.getNamingStrategy(), property), kind, conversion, argumentIndex);
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
        if (alias != null && !alias.isEmpty()) {
            return alias;
        }
        return namingStrategy.mappedName(Collections.emptyList(), property);
    }

    /**
     * @return The read kind of a data type, the typed getter {@code readDynamic} uses for it, or {@link #SKIPPED}
     * for a data type that is read through {@code getObject}
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
            default -> SKIPPED;
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

    /**
     * @return The mapper the annotation processor generated for the entity, which reads the rows in place of the
     * plan's columns, or {@code null} when the plan's columns are read one by one
     */
    @Nullable
    public GeneratedRowMapper<R> getGeneratedMapper() {
        return generatedMapper;
    }

    /**
     * Resolves what the generated mapper needs beyond the result set: the attribute converter of every column that
     * has one, with the conversion context of its property, as the general mapper applies them.
     *
     * @param conversionService        The conversion service
     * @param conversionContextFactory The factory of the conversion contexts, or {@code null} for plain ones
     * @return The context
     * @throws IllegalStateException if the plan holds no generated mapper
     */
    @SuppressWarnings("unchecked")
    public GeneratedRowMapperContext createContext(ConversionService conversionService,
                                                   @Nullable DatabaseConversionContextFactory conversionContextFactory) {
        if (generatedMapper == null) {
            throw new IllegalStateException("The plan of [" + entity.getName() + "] has no generated mapper");
        }
        String[] paths = generatedMapper.propertyPaths();
        AttributeConverter<Object, Object>[] converters = new AttributeConverter[paths.length];
        ConversionContext[] contexts = new ConversionContext[paths.length];
        for (int i = 0; i < paths.length; i++) {
            RuntimePersistentProperty<?> property = (RuntimePersistentProperty<?>) entity.getPropertyByPath(paths[i]).orElse(null);
            if (property == null) {
                continue;
            }
            AttributeConverter<Object, Object> converter = property.getConverter();
            if (converter != null) {
                converters[i] = converter;
                contexts[i] = conversionContextFactory != null
                    ? conversionContextFactory.forArgument(property.getArgument())
                    : ConversionContext.of(property.getArgument());
            }
        }
        return new GeneratedRowMapperContext(conversionService, converters, contexts);
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

    boolean isEmbeddable() {
        return embeddable;
    }

    /**
     * How one property is read while the plan is being resolved.
     *
     * @param property      The property
     * @param name          The column
     * @param kind          The read kind, or {@link #SKIPPED}
     * @param conversion    Whether the read value has to be converted to the property type
     * @param argumentIndex The constructor argument the property supplies, or {@code -1}
     * @param <R>           The entity type
     */
    private record Column<R>(RuntimePersistentProperty<R> property,
                             String name,
                             byte kind,
                             boolean conversion,
                             int argumentIndex) {
    }
}
