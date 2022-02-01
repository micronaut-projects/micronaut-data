/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.mongodb.operations;

import com.mongodb.client.model.Sorts;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.document.model.query.builder.MongoQueryBuilder;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.runtime.AttributeConverterRegistry;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.RuntimeAssociation;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.operations.HintsCapableRepository;
import io.micronaut.data.runtime.config.DataSettings;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.date.DateTimeProvider;
import io.micronaut.data.runtime.operations.internal.AbstractRepositoryOperations;
import io.micronaut.data.runtime.query.DefaultPreparedQueryResolver;
import io.micronaut.data.runtime.query.DefaultStoredQueryResolver;
import io.micronaut.data.runtime.query.PreparedQueryResolver;
import io.micronaut.data.runtime.query.StoredQueryResolver;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonNull;
import org.bson.BsonObjectId;
import org.bson.BsonValue;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Shared implementation of Mongo sync and reactive repositories.
 *
 * @param <Cnt> The connection
 * @param <PS>  The prepared statement
 * @author Denis Stepanov
 * @since 3.3
 */
@Internal
abstract class AbstractMongoRepositoryOperations<Cnt, PS> extends AbstractRepositoryOperations<Cnt, PS> implements HintsCapableRepository, PreparedQueryResolver, StoredQueryResolver {

    protected static final Logger QUERY_LOG = DataSettings.QUERY_LOG;
    protected static final BsonDocument EMPTY = new BsonDocument();
    protected final Map<Class, String> repoDatabaseConfig;

    private final DefaultStoredQueryResolver defaultStoredQueryResolver = new DefaultStoredQueryResolver() {
        @Override
        protected HintsCapableRepository getHintsCapableRepository() {
            return AbstractMongoRepositoryOperations.this;
        }
    };

    private final DefaultPreparedQueryResolver defaultPreparedQueryResolver = new DefaultPreparedQueryResolver() {
        @Override
        protected ConversionService getConversionService() {
            return conversionService;
        }
    };

    /**
     * Default constructor.
     *
     * @param server                     The server
     * @param beanContext                The bean context
     * @param codecs                     The media type codecs
     * @param dateTimeProvider           The date time provider
     * @param runtimeEntityRegistry      The entity registry
     * @param conversionService          The conversion service
     * @param attributeConverterRegistry The attribute converter registry
     */
    protected AbstractMongoRepositoryOperations(String server,
                                                BeanContext beanContext,
                                                List<MediaTypeCodec> codecs,
                                                DateTimeProvider<Object> dateTimeProvider,
                                                RuntimeEntityRegistry runtimeEntityRegistry,
                                                DataConversionService<?> conversionService,
                                                AttributeConverterRegistry attributeConverterRegistry) {
        super(codecs, dateTimeProvider, runtimeEntityRegistry, conversionService, attributeConverterRegistry);
        Collection<BeanDefinition<GenericRepository>> beanDefinitions = beanContext
                .getBeanDefinitions(GenericRepository.class, Qualifiers.byStereotype(MongoRepository.class));
        HashMap<Class, String> repoDatabaseConfig = new HashMap<>();
        for (BeanDefinition<GenericRepository> beanDefinition : beanDefinitions) {
            String targetSrv = beanDefinition.stringValue(Repository.class).orElse(null);
            if (targetSrv == null || targetSrv.isEmpty() || targetSrv.equalsIgnoreCase(server)) {
                String database = beanDefinition.stringValue(MongoRepository.class, "databaseName").orElse(null);
                if (StringUtils.isNotEmpty(database)) {
                    repoDatabaseConfig.put(beanDefinition.getBeanType(), database);
                }
            }
        }
        this.repoDatabaseConfig = Collections.unmodifiableMap(repoDatabaseConfig);
    }

    @Override
    public <E, R> PreparedQuery<E, R> resolveQuery(MethodInvocationContext<?, ?> context,
                                                   StoredQuery<E, R> storedQuery,
                                                   Pageable pageable) {
        return new DefaultMongoPreparedQuery<>(defaultPreparedQueryResolver.resolveQuery(context, storedQuery, pageable));
    }

    @Override
    public <E, R> PreparedQuery<E, R> resolveCountQuery(MethodInvocationContext<?, ?> context,
                                                        StoredQuery<E, R> storedQuery,
                                                        Pageable pageable) {
        return new DefaultMongoPreparedQuery<>(defaultPreparedQueryResolver.resolveCountQuery(context, storedQuery, pageable));
    }

    @Override
    public <E, R> StoredQuery<E, R> resolveQuery(MethodInvocationContext<?, ?> context, Class<E> entityClass, Class<R> resultType) {
        return new DefaultMongoStoredQuery<>(defaultStoredQueryResolver.resolveQuery(context, entityClass, resultType));
    }

    @Override
    public <E, R> StoredQuery<E, R> resolveCountQuery(MethodInvocationContext<?, ?> context, Class<E> entityClass, Class<R> resultType) {
        return new DefaultMongoStoredQuery<>(defaultStoredQueryResolver.resolveCountQuery(context, entityClass, resultType));
    }

    @Override
    public <E, QR> StoredQuery<E, QR> createStoredQuery(String name, AnnotationMetadata annotationMetadata,
                                                        Class<Object> rootEntity,
                                                        String query,
                                                        String update,
                                                        String[] queryParts,
                                                        List<QueryParameterBinding> queryParameters,
                                                        boolean hasPageable,
                                                        boolean isSingleResult) {
        StoredQuery<E, QR> storedQuery = defaultStoredQueryResolver.createStoredQuery(name, annotationMetadata,
                rootEntity, query, update, queryParts, queryParameters, hasPageable, isSingleResult);
        return new DefaultMongoStoredQuery<>(storedQuery, update);
    }

    @Override
    public StoredQuery<Object, Long> createCountStoredQuery(String name,
                                                            AnnotationMetadata annotationMetadata,
                                                            Class<Object> rootEntity,
                                                            String query,
                                                            String[] queryParts,
                                                            List<QueryParameterBinding> queryParameters) {
        StoredQuery<Object, Long> storedQuery = defaultStoredQueryResolver.createCountStoredQuery(name, annotationMetadata,
                rootEntity, query, queryParts, queryParameters);
        return new DefaultMongoStoredQuery<>(storedQuery);
    }

    protected FetchOptions getFetchOptions(CodecRegistry codecRegistry, PreparedQuery<?, ?> preparedQuery, RuntimePersistentEntity<?> persistentEntity) {
        MongoPreparedQuery<?, ?> mongoPreparedQuery = (MongoPreparedQuery<?, ?>) preparedQuery;
        BsonArray pipeline = mongoPreparedQuery.getPipeline();
        if (pipeline != null) {
            pipeline = (BsonArray) replaceQueryParameters(codecRegistry, preparedQuery, persistentEntity, pipeline);
            List<BsonDocument> preparedPipeline = pipeline.stream().map(BsonValue::asDocument).collect(Collectors.toList());
            return new FetchOptions(preparedPipeline, null);
        }
        BsonDocument filter = mongoPreparedQuery.getFilter();
        if (filter != null) {
            BsonDocument preparedFilter = getQuery(codecRegistry, preparedQuery, persistentEntity, filter);
            return new FetchOptions(null, preparedFilter);
        }
        return new FetchOptions(null, EMPTY);
    }

    protected UpdateOptions getUpdateOptions(CodecRegistry codecRegistry, PreparedQuery<?, ?> preparedQuery, RuntimePersistentEntity<?> persistentEntity) {
        MongoPreparedQuery<?, ?> mongoPreparedQuery = (MongoPreparedQuery<?, ?>) preparedQuery;
        BsonDocument update = mongoPreparedQuery.getUpdate();
        if (update == null) {
            throw new IllegalArgumentException("Update query is not provided!");
        }
        BsonDocument preparedUpdate = getQuery(codecRegistry, preparedQuery, persistentEntity, update);
        BsonDocument preparedFilter;
        BsonDocument filter = mongoPreparedQuery.getFilter();
        if (filter != null) {
            preparedFilter = getQuery(codecRegistry, preparedQuery, persistentEntity, filter);
        } else {
            preparedFilter = null;
        }
        return new UpdateOptions(preparedUpdate, preparedFilter);
    }

    protected DeleteOptions getDeleteOptions(CodecRegistry codecRegistry, PreparedQuery<?, ?> preparedQuery, RuntimePersistentEntity<?> persistentEntity) {
        MongoPreparedQuery<?, ?> mongoPreparedQuery = (MongoPreparedQuery<?, ?>) preparedQuery;
        BsonDocument preparedFilter;
        BsonDocument filter = mongoPreparedQuery.getFilter();
        if (filter != null) {
            preparedFilter = getQuery(codecRegistry, preparedQuery, persistentEntity, filter);
        } else {
            preparedFilter = null;
        }
        return new DeleteOptions(preparedFilter);
    }

    protected <R> R convertResult(CodecRegistry codecRegistry,
                                  Class<R> resultType,
                                  BsonDocument result,
                                  boolean isDtoProjection) {
        BsonValue value;
        if (result == null) {
            value = BsonNull.VALUE;
        } else if (result.size() == 1) {
            value = result.values().iterator().next().asNumber();
        } else if (result.size() == 2) {
            value = result.entrySet().stream().filter(f -> !f.getKey().equals("_id")).findFirst().get().getValue();
        } else if (isDtoProjection) {
            Object dtoResult = MongoUtils.toValue(result.asDocument(), resultType, codecRegistry);
            if (resultType.isInstance(dtoResult)) {
                return (R) dtoResult;
            }
            return conversionService.convertRequired(dtoResult, resultType);
        } else {
            throw new IllegalStateException("Unrecognized result: " + result);
        }
        return conversionService.convertRequired(MongoUtils.toValue(value), resultType);
    }

    protected int applyPageable(Pageable pageable, List<BsonDocument> pipeline) {
        int limit = 0;
        if (pageable != Pageable.UNPAGED) {
            int skip = (int) pageable.getOffset();
            limit = pageable.getSize();
            Sort pageableSort = pageable.getSort();
            if (pageableSort.isSorted()) {
                Bson sort = pageableSort.getOrderBy().stream().map(order -> order.isAscending() ? Sorts.ascending(order.getProperty()) : Sorts.descending(order.getProperty())).collect(Collectors.collectingAndThen(Collectors.toList(), Sorts::orderBy));
                BsonDocument sortStage = new BsonDocument().append("$sort", sort.toBsonDocument());
                addStageToPipelineBefore(pipeline, sortStage, "$limit", "$skip");
            }
            if (skip > 0) {
                pipeline.add(new BsonDocument().append("$skip", new BsonInt32(skip)));
            }
            if (limit > 0) {
                pipeline.add(new BsonDocument().append("$limit", new BsonInt32(limit)));
            }
        }
        return limit;
    }

    protected <T, R> boolean isCountQuery(PreparedQuery<T, R> preparedQuery) {
        return preparedQuery.isCount() || preparedQuery.getQuery().contains("$count");
    }

    private void addStageToPipelineBefore(List<BsonDocument> pipeline, BsonDocument stageToAdd, String... beforeStages) {
        int lastFoundIndex = -1;
        int index = 0;
        for (BsonDocument stage : pipeline) {
            for (String beforeStageName : beforeStages) {
                if (stage.containsKey(beforeStageName)) {
                    lastFoundIndex = index;
                    break;
                }
            }
            index++;
        }
        if (lastFoundIndex > -1) {
            pipeline.add(lastFoundIndex, stageToAdd);
        } else {
            pipeline.add(stageToAdd);
        }
    }

    private <T> BsonDocument getQuery(CodecRegistry codecRegistry, PreparedQuery<?, ?> preparedQuery, RuntimePersistentEntity<T> persistentEntity, BsonDocument bsonDocument) {
        if (bsonDocument == null) {
            return EMPTY;
        }
        return (BsonDocument) replaceQueryParameters(codecRegistry, preparedQuery, persistentEntity, bsonDocument);
    }

    private <T> BsonValue replaceQueryParameters(CodecRegistry codecRegistry, PreparedQuery<?, ?> preparedQuery, RuntimePersistentEntity<T> persistentEntity, BsonValue value) {
        if (value instanceof BsonDocument) {
            BsonDocument bsonDocument = (BsonDocument) value;
            BsonInt32 queryParameterIndex = bsonDocument.getInt32(MongoQueryBuilder.QUERY_PARAMETER_PLACEHOLDER, null);
            if (queryParameterIndex != null) {
                int index = queryParameterIndex.getValue();
                return getValue(index, preparedQuery.getQueryBindings().get(index), preparedQuery, persistentEntity, codecRegistry);
            }

            for (Map.Entry<String, BsonValue> entry : bsonDocument.entrySet()) {
                BsonValue bsonValue = entry.getValue();
                BsonValue newValue = replaceQueryParameters(codecRegistry, preparedQuery, persistentEntity, bsonValue);
                if (bsonValue != newValue) {
                    entry.setValue(newValue);
                }
            }
            return bsonDocument;
        } else if (value instanceof BsonArray) {
            BsonArray bsonArray = (BsonArray) value;
            for (int i = 0; i < bsonArray.size(); i++) {
                BsonValue bsonValue = bsonArray.get(i);
                BsonValue newValue = replaceQueryParameters(codecRegistry, preparedQuery, persistentEntity, bsonValue);
                if (bsonValue != newValue) {
                    if (newValue.isNull()) {
                        bsonArray.remove(i);
                        i -= 1;
                    } else if (newValue.isArray()) {
                        bsonArray.remove(i);
                        List<BsonValue> values = newValue.asArray().getValues();
                        bsonArray.addAll(i, values);
                        i += values.size() - 1;
                    } else {
                        bsonArray.set(i, newValue);
                    }
                }
            }
        }
        return value;
    }

    private <T> BsonValue getValue(int index,
                                   QueryParameterBinding queryParameterBinding,
                                   PreparedQuery<?, ?> preparedQuery,
                                   RuntimePersistentEntity<T> persistentEntity,
                                   CodecRegistry codecRegistry) {
        Class<?> parameterConverter = queryParameterBinding.getParameterConverterClass();
        Object value;
        if (queryParameterBinding.getParameterIndex() != -1) {
            value = resolveParameterValue(queryParameterBinding, preparedQuery.getParameterArray());
        } else if (queryParameterBinding.isAutoPopulated()) {
            PersistentPropertyPath pp = getRequiredPropertyPath(queryParameterBinding, persistentEntity);
            RuntimePersistentProperty<?> persistentProperty = (RuntimePersistentProperty) pp.getProperty();
            Object previousValue = null;
            QueryParameterBinding previousPopulatedValueParameter = queryParameterBinding.getPreviousPopulatedValueParameter();
            if (previousPopulatedValueParameter != null) {
                if (previousPopulatedValueParameter.getParameterIndex() == -1) {
                    throw new IllegalStateException("Previous value parameter cannot be bind!");
                }
                previousValue = resolveParameterValue(previousPopulatedValueParameter, preparedQuery.getParameterArray());
            }
            value = runtimeEntityRegistry.autoPopulateRuntimeProperty(persistentProperty, previousValue);
            value = convert(value, persistentProperty);
            parameterConverter = null;
        } else {
            throw new IllegalStateException("Invalid query [" + "]. Unable to establish parameter value for parameter at position: " + (index + 1));
        }

        DataType dataType = queryParameterBinding.getDataType();
        List<Object> values = expandValue(value, dataType);
        if (values != null && values.isEmpty()) {
            // Empty collections / array should always set at least one value
            value = null;
            values = null;
        }
        if (values == null) {
            if (parameterConverter != null) {
                int parameterIndex = queryParameterBinding.getParameterIndex();
                Argument<?> argument = parameterIndex > -1 ? preparedQuery.getArguments()[parameterIndex] : null;
                value = convert(parameterConverter, value, argument);
            }
            if (value instanceof String) {
                PersistentPropertyPath pp = getRequiredPropertyPath(queryParameterBinding, persistentEntity);
                RuntimePersistentProperty<?> persistentProperty = (RuntimePersistentProperty) pp.getProperty();
                if (persistentProperty instanceof RuntimeAssociation) {
                    RuntimeAssociation runtimeAssociation = (RuntimeAssociation) persistentProperty;
                    RuntimePersistentProperty identity = runtimeAssociation.getAssociatedEntity().getIdentity();
                    if (identity != null && identity.getType() == String.class && identity.isGenerated()) {
                        return new BsonObjectId(new ObjectId((String) value));
                    }
                }
                if (persistentProperty.getOwner().getIdentity() == persistentProperty && persistentProperty.getType() == String.class && persistentProperty.isGenerated()) {
                    return new BsonObjectId(new ObjectId((String) value));
                }
            }
            return MongoUtils.toBsonValue(conversionService, value, codecRegistry);
        } else {
            Class<?> finalParameterConverter = parameterConverter;
            return new BsonArray(values.stream().map(val -> {
                if (finalParameterConverter != null) {
                    int parameterIndex = queryParameterBinding.getParameterIndex();
                    Argument<?> argument = parameterIndex > -1 ? preparedQuery.getArguments()[parameterIndex] : null;
                    val = convert(finalParameterConverter, val, argument);
                }
                return MongoUtils.toBsonValue(conversionService, val, codecRegistry);
            }).collect(Collectors.toList()));
        }
    }

    private Object convert(Class<?> converterClass, Object value, @Nullable Argument<?> argument) {
        if (converterClass == null) {
            return value;
        }
        AttributeConverter<Object, Object> converter = attributeConverterRegistry.getConverter(converterClass);
        ConversionContext conversionContext = createTypeConversionContext(null, null, argument);
        return converter.convertToPersistedValue(value, conversionContext);
    }

    private Object convert(Object value, RuntimePersistentProperty<?> property) {
        AttributeConverter<Object, Object> converter = property.getConverter();
        if (converter != null) {
            return converter.convertToPersistedValue(value, createTypeConversionContext(null, property, property.getArgument()));
        }
        return value;
    }

    private <T> PersistentPropertyPath getRequiredPropertyPath(QueryParameterBinding queryParameterBinding, RuntimePersistentEntity<T> persistentEntity) {
        String[] propertyPath = queryParameterBinding.getRequiredPropertyPath();
        PersistentPropertyPath pp = persistentEntity.getPropertyPath(propertyPath);
        if (pp == null) {
            throw new IllegalStateException("Cannot find auto populated property: " + String.join(".", propertyPath));
        }
        return pp;
    }

    private List<Object> expandValue(Object value, DataType dataType) {
        // Special case for byte array, we want to support a list of byte[] convertible values
        if (value == null || dataType != null && dataType.isArray() && dataType != DataType.BYTE_ARRAY || value instanceof byte[]) {
            // not expanded
            return null;
        } else if (value instanceof Iterable) {
            return (List<Object>) CollectionUtils.iterableToList((Iterable<?>) value);
        } else if (value.getClass().isArray()) {
            int len = Array.getLength(value);
            if (len == 0) {
                return Collections.emptyList();
            } else {
                List<Object> list = new ArrayList<>(len);
                for (int j = 0; j < len; j++) {
                    Object o = Array.get(value, j);
                    list.add(o);
                }
                return list;
            }
        } else {
            // not expanded
            return null;
        }
    }

    private Object resolveParameterValue(QueryParameterBinding queryParameterBinding, Object[] parameterArray) {
        Object value;
        value = parameterArray[queryParameterBinding.getParameterIndex()];
        String[] parameterBindingPath = queryParameterBinding.getParameterBindingPath();
        if (parameterBindingPath != null) {
            for (String prop : parameterBindingPath) {
                if (value == null) {
                    return null;
                }
                Object finalValue = value;
                BeanProperty beanProperty = BeanIntrospection.getIntrospection(value.getClass())
                        .getProperty(prop).orElseThrow(() -> new IntrospectionException("Cannot find a property: '" + prop + "' on bean: " + finalValue));
                value = beanProperty.get(value);
            }
        }
        return value;
    }

    protected BsonDocument association(CodecRegistry codecRegistry,
                                       Object value, RuntimePersistentEntity<Object> persistentEntity,
                                       Object child, RuntimePersistentEntity<Object> childPersistentEntity) {
        BsonDocument document = new BsonDocument();
        document.put(persistentEntity.getPersistedName(), MongoUtils.entityIdValue(conversionService, persistentEntity, value, codecRegistry));
        document.put(childPersistentEntity.getPersistedName(), MongoUtils.entityIdValue(conversionService, childPersistentEntity, child, codecRegistry));
        return document;
    }

    @Override
    protected ConversionContext createTypeConversionContext(Cnt connection, RuntimePersistentProperty<?> property, Argument<?> argument) {
        if (argument != null) {
            return ConversionContext.of(argument);
        }
        return ConversionContext.DEFAULT;
    }

    protected static final class FetchOptions {

        final List<BsonDocument> pipeline;
        final BsonDocument filter;

        public FetchOptions(@Nullable List<BsonDocument> pipeline, @Nullable BsonDocument filter) {
            this.pipeline = pipeline;
            this.filter = filter;
        }
    }

    protected static final class UpdateOptions {

        final BsonDocument update;
        final BsonDocument filter;

        public UpdateOptions(@Nullable BsonDocument update, @Nullable BsonDocument filter) {
            this.update = update;
            this.filter = filter;
        }
    }

    protected static final class DeleteOptions {

        final BsonDocument filter;

        public DeleteOptions(@Nullable BsonDocument filter) {
            this.filter = filter == null ? EMPTY : filter;
        }
    }

}
