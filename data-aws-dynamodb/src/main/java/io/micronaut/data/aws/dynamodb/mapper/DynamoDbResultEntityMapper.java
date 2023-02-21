/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.data.aws.dynamodb.mapper;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.reflect.exception.InstantiationException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.EmbeddedId;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.Embedded;
import io.micronaut.data.model.naming.NamingStrategies;
import io.micronaut.data.model.naming.NamingStrategy;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.mapper.sql.SqlTypeMapper;
import io.micronaut.http.codec.MediaTypeCodec;

import java.sql.Array;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Mapper that reads DynamoDB results into entity models.
 *
 * @author radovanradic
 * @since 4.0.0
 * @param <R> The result type
 */
public class DynamoDbResultEntityMapper<R> implements SqlTypeMapper<List<Map<String, AttributeValue>>, R> {

    private static final NamingStrategy RAW_NAMING_STRATEGY = new NamingStrategies.Raw();

    private final RuntimePersistentEntity<R> persistentEntity;
    private final DynamoDbResultReader resultReader;
    private final MediaTypeCodec jsonCodec;
    private final DataConversionService conversionService;

    public DynamoDbResultEntityMapper(@NonNull RuntimePersistentEntity<R> persistentEntity,
                                      @NonNull DynamoDbResultReader resultReader,
                                      @Nullable MediaTypeCodec jsonCodec,
                                      DataConversionService conversionService) {
        this.persistentEntity = persistentEntity;
        this.resultReader = resultReader;
        this.jsonCodec = jsonCodec;
        this.conversionService = conversionService;
    }

    @Override
    @NonNull
    public DataConversionService getConversionService() {
        return conversionService;
    }

    @NonNull
    @Override
    public R map(@NonNull List<Map<String, AttributeValue>> rs, @NonNull Class<R> type) throws DataAccessException {
        R entityInstance = readEntity(rs, DynamoDbResultEntityMapper.MappingContext.of(persistentEntity), null, null);
        if (entityInstance == null) {
            throw new DataAccessException("Unable to map result to entity of type [" + type.getName() + "]. Missing result data.");
        }
        return entityInstance;
    }

    @Nullable
    @Override
    public Object read(@NonNull List<Map<String, AttributeValue>> resultSet, @NonNull String name) {
        RuntimePersistentProperty<R> property = persistentEntity.getPropertyByName(name);
        if (property == null) {
            throw new DataAccessException("DTO projection defines a property [" + name + "] that doesn't exist on root entity: " + persistentEntity.getName());
        }
        String columnName = property.getPersistedName();
        return resultReader.getRequiredValue(resultSet, columnName, property.getType());
    }

    @Nullable
    @Override
    public Object read(@NonNull List<Map<String, AttributeValue>> resultSet, @NonNull Argument<?> argument) {
        RuntimePersistentProperty<R> property = persistentEntity.getPropertyByName(argument.getName());
        if (property == null) {
            // TODO: Is this case valid, throw an error?
            return null;
        }
        return resultReader.getRequiredValue(resultSet, property.getPersistedName(), property.getType());
    }

    @Override
    public boolean hasNext(List<Map<String, AttributeValue>> resultSet) {
        return resultReader.next(resultSet);
    }

    private <X, Y> X setProperty(BeanProperty<X, Y> beanProperty, X x, Y y) {
        if (beanProperty.isReadOnly()) {
            return beanProperty.withValue(x, y);
        }
        beanProperty.set(x, y);
        return x;
    }

    @Nullable
    private <K> K readEntity(List<Map<String, AttributeValue>> rs, DynamoDbResultEntityMapper.MappingContext<K> ctx, @Nullable Object parent, @Nullable Object resolveId) {
        RuntimePersistentEntity<K> ctxPersistentEntity = ctx.persistentEntity;
        BeanIntrospection<K> introspection = ctxPersistentEntity.getIntrospection();
        RuntimePersistentProperty<K>[] constructorArguments = ctxPersistentEntity.getConstructorArguments();
        try {
            RuntimePersistentProperty<K> identity = ctxPersistentEntity.getIdentity();
            final boolean isAssociation = ctx.association != null;
            final boolean isEmbedded = ctx.association instanceof Embedded;
            final boolean nullableEmbedded = isEmbedded && ctx.association.isOptional();

            Object id = resolveId == null ? readEntityId(rs, ctx) : resolveId;
            if (id == null && !isEmbedded && isAssociation) {
                return null;
            }

            K entity;
            if (ArrayUtils.isEmpty(constructorArguments)) {
                entity = introspection.instantiate();
            } else {
                int len = constructorArguments.length;
                Object[] args = new Object[len];
                for (int i = 0; i < len; i++) {
                    RuntimePersistentProperty<K> prop = constructorArguments[i];
                    if (prop != null) {
                        if (prop instanceof Association) {
                            if (prop instanceof Embedded embedded) {
                                args[i] = readEntity(rs, ctx.embedded(embedded), null, null);
                            } else {
                                throw new DataAccessException("Only Embedded relation is supported in Micronaut Data AWS DynamoDB");
                            }
                        } else {
                            Object v;
                            if (resolveId != null && prop.equals(identity)) {
                                v = resolveId;
                            } else {
                                v = readProperty(rs, ctx, prop);
                                if (v == null) {
                                    if (!prop.isOptional() && !nullableEmbedded) {
                                        AnnotationMetadata entityAnnotationMetadata = ctx.persistentEntity.getAnnotationMetadata();
                                        if (entityAnnotationMetadata.hasAnnotation(Embeddable.class) || entityAnnotationMetadata.hasAnnotation(EmbeddedId.class)) {
                                            return null;
                                        }
                                        throw new DataAccessException("Null value read for non-null constructor argument [" + prop.getName() + "] of type: " + ctxPersistentEntity.getName());
                                    } else {
                                        args[i] = null;
                                        continue;
                                    }
                                }
                            }
                            args[i] = convert(prop, v);
                        }
                    } else {
                        throw new DataAccessException("Constructor argument [" + constructorArguments[i].getName() + "] must have an associated getter.");
                    }
                }
                if (nullableEmbedded && args.length > 0 && Arrays.stream(args).allMatch(Objects::isNull)) {
                    return null;
                } else {
                    entity = introspection.instantiate(args);
                }
            }

            if (id != null && identity != null) {
                BeanProperty<K, Object> idProperty = identity.getProperty();
                entity = (K) convertAndSetWithValue(entity, identity, idProperty, id);
            }
            RuntimePersistentProperty<K> version = ctxPersistentEntity.getVersion();
            if (version != null) {
                Object v = readProperty(rs, ctx, version);
                if (v != null) {
                    entity = (K) convertAndSetWithValue(entity, version, version.getProperty(), v);
                }
            }
            for (RuntimePersistentProperty<K> rpp : ctxPersistentEntity.getPersistentProperties()) {
                if (rpp.isReadOnly()) {
                    continue;
                } else if (rpp.isConstructorArgument()) {
                    if (rpp instanceof Association a) {
                        final Relation.Kind kind = a.getKind();
                        if (kind.isSingleEnded()) {
                            continue;
                        }
                    } else {
                        continue;
                    }
                }
                BeanProperty<K, Object> property = rpp.getProperty();
                if (rpp instanceof Association) {
                    if (rpp instanceof Embedded) {
                        Object value = readEntity(rs, ctx.embedded((Embedded) rpp), parent == null ? entity : parent, null);
                        entity = setProperty(property, entity, value);
                    } else {
                        throw new DataAccessException("Only Embedded relation is supported in Micronaut Data AWS DynamoDB");
                    }
                } else {
                    Object v = readProperty(rs, ctx, rpp);
                    if (v != null) {
                        entity = (K) convertAndSetWithValue(entity, rpp, property, v);
                    }
                }
            }
            return entity;
        } catch (InstantiationException e) {
            throw new DataAccessException("Error instantiating entity [" + ctxPersistentEntity.getName() + "]: " + e.getMessage(), e);
        }
    }

    private <K> Object readProperty(List<Map<String, AttributeValue>> rs, DynamoDbResultEntityMapper.MappingContext<K> ctx, RuntimePersistentProperty<K> prop) {
        String columnName = ctx.namingStrategy.mappedName(ctx.embeddedPath, prop);
        String columnAlias = prop.getAnnotationMetadata().stringValue(MappedProperty.class, MappedProperty.ALIAS).orElse("");
        if (StringUtils.isNotEmpty(columnAlias)) {
            columnName = columnAlias;
        }
        Object result = resultReader.getRequiredValue(rs, columnName, prop.getType());
        AttributeConverter<Object, Object> converter = prop.getConverter();
        if (converter != null) {
            return converter.convertToEntityValue(result, ConversionContext.of(prop.getArgument()));
        }
        return result;
    }

    private @Nullable
    <K> Object readEntityId(List<Map<String, AttributeValue>> rs, DynamoDbResultEntityMapper.MappingContext<K> ctx) {
        RuntimePersistentProperty<K> identity = ctx.persistentEntity.getIdentity();
        if (identity == null) {
            return null;
        }
        if (identity instanceof Embedded embedded) {
            return readEntity(rs, ctx.embedded(embedded), null, null);
        }
        return readProperty(rs, ctx, identity);
    }

    private Object convertAndSetWithValue(Object entity, RuntimePersistentProperty<?> rpp, BeanProperty property, Object v) {
        return setProperty(property, entity, convert(rpp, v));
    }

    private Object convert(RuntimePersistentProperty<?> rpp, Object v) {
        Class<?> propertyType = rpp.getType();
        if (v instanceof Array) {
            try {
                v = ((Array) v).getArray();
            } catch (SQLException e) {
                throw new DataAccessException("Error getting an array value: " + e.getMessage(), e);
            }
        }
        if (propertyType.isInstance(v)) {
            return v;
        }
        if (jsonCodec != null && rpp.getDataType() == DataType.JSON) {
            try {
                return jsonCodec.decode(rpp.getArgument(), v.toString());
            } catch (Exception e) {
                // Ignore and try basic convert
            }
        }
        return resultReader.convertRequired(v, rpp.getArgument());
    }

    private static final class MappingContext<E> {

        private final RuntimePersistentEntity<E> rootPersistentEntity;
        private final RuntimePersistentEntity<E> persistentEntity;
        private final NamingStrategy namingStrategy;
        private final List<Association> embeddedPath;
        private final Association association;

        private Map<Association, DynamoDbResultEntityMapper.MappingContext> associations;

        private MappingContext(RuntimePersistentEntity rootPersistentEntity,
                               RuntimePersistentEntity persistentEntity,
                               NamingStrategy namingStrategy,
                               List<Association> embeddedPath,
                               Association association) {
            this.rootPersistentEntity = rootPersistentEntity;
            this.persistentEntity = persistentEntity;
            this.namingStrategy = namingStrategy;
            this.embeddedPath = embeddedPath;
            this.association = association;
        }

        public static <K> DynamoDbResultEntityMapper.MappingContext<K> of(RuntimePersistentEntity<K> persistentEntity) {
            return new DynamoDbResultEntityMapper.MappingContext<>(
                persistentEntity,
                persistentEntity,
                persistentEntity.findNamingStrategy().orElse(RAW_NAMING_STRATEGY),
                Collections.emptyList(),
                null);
        }

        public <K> DynamoDbResultEntityMapper.MappingContext<K> embedded(Embedded embedded) {
            if (associations == null) {
                associations = new LinkedHashMap<>();
            }
            return associations.computeIfAbsent(embedded, e -> embeddedAssociation(embedded));
        }

        private <K> DynamoDbResultEntityMapper.MappingContext<K> embeddedAssociation(Embedded embedded) {
            RuntimePersistentEntity<K> associatedEntity = (RuntimePersistentEntity) embedded.getAssociatedEntity();
            return new DynamoDbResultEntityMapper.MappingContext<>(
                rootPersistentEntity,
                associatedEntity,
                associatedEntity.findNamingStrategy().orElse(namingStrategy),
                associated(embeddedPath, embedded),
                embedded
            );
        }

        private static List<Association> associated(List<Association> associations, Association association) {
            List<Association> newAssociations = new ArrayList<>(associations.size() + 1);
            newAssociations.addAll(associations);
            newAssociations.add(association);
            return newAssociations;
        }

    }
}
