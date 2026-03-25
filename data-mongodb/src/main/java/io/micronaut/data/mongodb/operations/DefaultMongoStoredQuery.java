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

import com.mongodb.client.model.Collation;
import com.mongodb.client.model.DeleteOptions;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.UpdateOptions;
import io.micronaut.aop.InvocationContext;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.document.model.query.builder.MongoQueryBuilder;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.runtime.AttributeConverterRegistry;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.RuntimeAssociation;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import io.micronaut.data.mongodb.annotation.MongoCollation;
import io.micronaut.data.mongodb.annotation.MongoProjection;
import io.micronaut.data.mongodb.annotation.MongoSort;
import io.micronaut.data.mongodb.operations.options.MongoAggregationOptions;
import io.micronaut.data.mongodb.operations.options.MongoFindOptions;
import io.micronaut.data.mongodb.operations.options.MongoOptionsUtils;
import io.micronaut.data.runtime.operations.internal.query.DefaultBindableParametersStoredQuery;
import io.micronaut.data.runtime.query.internal.DefaultStoredQuery;
import io.micronaut.data.runtime.query.internal.DelegateStoredQuery;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonDocumentWrapper;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonObjectId;
import org.bson.BsonRegularExpression;
import org.bson.BsonValue;
import org.bson.codecs.Encoder;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default implementation of {@link MongoStoredQuery}.
 *
 * @param <E> The entity type
 * @param <R> The result type
 * @author Denis Stepanov
 * @since 3.3.
 */
@Internal
final class DefaultMongoStoredQuery<E, R> extends DefaultBindableParametersStoredQuery<E, R> implements DelegateStoredQuery<E, R>, MongoStoredQuery<E, R> {

    private static final Pattern MONGO_PARAM_PATTERN = Pattern.compile("\\W*(\\" + MongoQueryBuilder.QUERY_PARAMETER_PLACEHOLDER + ":(\\d)+)\\W*");
    private static final Logger LOG = LoggerFactory.getLogger(DefaultMongoStoredQuery.class);
    private static final BsonDocument EMPTY = new BsonDocument();

    private final StoredQuery<E, R> storedQuery;
    private final Supplier<CodecRegistry> codecRegistry;
    private final AttributeConverterRegistry attributeConverterRegistry;
    private final RuntimeEntityRegistry runtimeEntityRegistry;
    private final ConversionService conversionService;
    private final RuntimePersistentEntity<E> persistentEntity;
    @Nullable
    private final UpdateData updateData;
    @Nullable
    private final UpdateReturningData updateReturningData;
    @Nullable
    private final FindData findData;
    @Nullable
    private final AggregateData aggregateData;
    @Nullable
    private final DeleteData deleteData;
    private final boolean isCount;

    DefaultMongoStoredQuery(StoredQuery<E, R> storedQuery,
                            Supplier<CodecRegistry> codecRegistry,
                            AttributeConverterRegistry attributeConverterRegistry,
                            RuntimeEntityRegistry runtimeEntityRegistry,
                            ConversionService conversionService,
                            RuntimePersistentEntity<E> persistentEntity) {
        this(storedQuery,
            codecRegistry,
            attributeConverterRegistry,
            runtimeEntityRegistry,
            conversionService,
            persistentEntity,
            storedQuery.getAnnotationMetadata().stringValue(Query.class, "update").orElse(null));
    }

    DefaultMongoStoredQuery(StoredQuery<E, R> storedQuery,
                            Supplier<CodecRegistry> codecRegistry,
                            AttributeConverterRegistry attributeConverterRegistry,
                            RuntimeEntityRegistry runtimeEntityRegistry,
                            ConversionService conversionService,
                            RuntimePersistentEntity<E> persistentEntity,
                            @Nullable String updateJson) {
        super(storedQuery, persistentEntity, conversionService);
        this.storedQuery = storedQuery;
        this.codecRegistry = codecRegistry;
        this.attributeConverterRegistry = attributeConverterRegistry;
        this.runtimeEntityRegistry = runtimeEntityRegistry;
        this.conversionService = conversionService;
        this.persistentEntity = persistentEntity;
        OperationType operationType = storedQuery.getOperationType();
        if (operationType == OperationType.QUERY || operationType == OperationType.EXISTS || operationType == OperationType.COUNT) {
            String query = storedQuery.getQuery();
            if (query != null) {
                query = query.trim();
            }
            String filterParameter = getParameterInRole(MongoRoles.FILTER_ROLE);
            String filterOptionsParameter = getParameterInRole(MongoRoles.FIND_OPTIONS_ROLE);
            String pipelineParameter = getParameterInRole(MongoRoles.PIPELINE_ROLE);
            if (filterParameter != null || filterOptionsParameter != null) {
                aggregateData = null;
                findData = new FindData(filterParameter, filterOptionsParameter);
            } else if (pipelineParameter != null) {
                aggregateData = new AggregateData(pipelineParameter, getParameterInRole(MongoRoles.AGGREGATE_OPTIONS_ROLE));
                findData = null;
            } else if (StringUtils.isEmpty(query)) {
                aggregateData = null;
                findData = new FindData(BsonDocument.parse(query));
            } else if (query.startsWith("[")) {
                aggregateData = new AggregateData(parseAggregation(query, storedQuery.isCount()));
                findData = null;
            } else {
                aggregateData = null;
                findData = new FindData(BsonDocument.parse(query));
            }
            isCount = operationType == OperationType.COUNT || storedQuery.isCount() || query.contains("$count");
        } else {
            aggregateData = null;
            findData = null;
            isCount = false;
        }

        if (operationType == OperationType.DELETE) {
            String query = storedQuery.getQuery();
            deleteData = new DeleteData(
                StringUtils.isEmpty(query) ? EMPTY : BsonDocument.parse(query),
                getParameterInRole(MongoRoles.FILTER_ROLE),
                getParameterInRole(MongoRoles.DELETE_OPTIONS_ROLE)
            );
        } else {
            deleteData = null;
        }

        if (operationType == OperationType.UPDATE) {
            if (StringUtils.isEmpty(updateJson)) {
                throw new IllegalStateException("Update query is expected!");
            }
            String query = storedQuery.getQuery();
            updateData = new UpdateData(
                BsonDocument.parse(updateJson), StringUtils.isEmpty(query) ? EMPTY : BsonDocument.parse(query),
                getParameterInRole(MongoRoles.FILTER_ROLE),
                getParameterInRole(MongoRoles.UPDATE_ROLE),
                getParameterInRole(MongoRoles.UPDATE_OPTIONS_ROLE)
            );
        } else {
            updateData = null;
        }

        if (operationType == OperationType.UPDATE_RETURNING) {
            if (StringUtils.isEmpty(updateJson)) {
                throw new IllegalStateException("Update query is expected!");
            }
            String query = storedQuery.getQuery();
            updateReturningData = new UpdateReturningData(
                BsonDocument.parse(updateJson), StringUtils.isEmpty(query) ? EMPTY : BsonDocument.parse(query),
                getParameterInRole(MongoRoles.FILTER_ROLE),
                getParameterInRole(MongoRoles.UPDATE_ROLE),
                getParameterInRole(MongoRoles.UPDATE_OPTIONS_ROLE)
            );
        } else {
            updateReturningData = null;
        }
    }

    private List<Bson> parseAggregation(String query, boolean isCount) {
        List<Bson> pipeline = BsonArray.parse(query).stream().<Bson>map(BsonValue::asDocument).toList();
        if (isCount && pipeline.stream().noneMatch(p -> p.toBsonDocument().containsKey("$count"))) {
            // We can probably remove sorting projection etc. or allow a user to specify a custom count pipeline
            List<Bson> countPipeline = new ArrayList<>(pipeline);
            countPipeline.add(BsonDocument.parse("{ $count: \"totalCount\" }"));
            return countPipeline;
        }
        return pipeline;
    }

    @Override
    public boolean isCount() {
        return isCount;
    }

    @Nullable
    private String getParameterInRole(String role) {
        if (storedQuery instanceof DefaultStoredQuery) {
            AnnotationValue<DataMethod> dataMethodAnnotationValue = storedQuery.getAnnotationMetadata().getAnnotation(DataMethod.class);
            if (dataMethodAnnotationValue != null) {
                return dataMethodAnnotationValue.stringValue(role).orElse(null);
            }
        }
        return null;
    }

    private int getParameterIndexByName(@Nullable String name) {
        if (name == null) {
            return -1;
        }
        if (storedQuery instanceof DefaultStoredQuery<?, ?> defaultStoredQuery) {
            String[] argumentNames = defaultStoredQuery.getMethod().getArgumentNames();
            for (int i = 0; i < argumentNames.length; i++) {
                String argumentName = argumentNames[i];
                if (argumentName.equals(name)) {
                    return i;
                }
            }
            throw new IllegalStateException("Unknown parameter with name: " + name);
        }
        throw new IllegalStateException("Expected DefaultStoredQuery");
    }

    @Nullable
    private <X> X getParameterAtIndex(@Nullable InvocationContext<?, ?> invocationContext, int index) {
        Objects.requireNonNull(invocationContext);
        return (X) invocationContext.getParameterValues()[index];
    }

    @Override
    public RuntimePersistentEntity<E> getRuntimePersistentEntity() {
        return persistentEntity;
    }

    @Override
    public boolean isAggregate() {
        return aggregateData != null;
    }

    @Override
    public MongoAggregation getAggregation(InvocationContext<?, ?> invocationContext) {
        if (aggregateData == null) {
            throw new IllegalStateException("Expected aggregation query!");
        }
        return aggregateData.getAggregation(invocationContext);
    }

    @Override
    public MongoFind getFind(InvocationContext<?, ?> invocationContext) {
        if (findData == null) {
            throw new IllegalStateException("Expected find query!");
        }
        return findData.getFind(invocationContext);
    }

    @Override
    public MongoUpdate getUpdateMany(InvocationContext<?, ?> invocationContext) {
        if (updateData == null) {
            throw new IllegalStateException("Expected update query!");
        }
        return updateData.getUpdateMany(invocationContext);

    }

    @Override
    public MongoUpdate getUpdateOne(E entity) {
        if (updateData == null) {
            throw new IllegalStateException("Expected update query!");
        }
        return updateData.getUpdateOne(entity);
    }

    @Override
    public MongoFindOneAndUpdate getUpdateOne(InvocationContext<?, ?> invocationContext) {
        if (updateReturningData == null) {
            throw new IllegalStateException("Expected update returning query!");
        }
        return updateReturningData.getUpdateOne(invocationContext);
    }

    @Override
    public MongoDelete getDeleteMany(InvocationContext<?, ?> invocationContext) {
        if (deleteData == null) {
            throw new IllegalStateException("Expected delete query!");
        }
        return deleteData.getDeleteMany(invocationContext);
    }

    @Override
    public MongoDelete getDeleteOne(E entity) {
        if (deleteData == null) {
            throw new IllegalStateException("Expected delete query!");
        }
        return deleteData.getDeleteOne(entity);
    }

    private boolean needsProcessing(@Nullable Bson value) {
        if (value == null) {
            return false;
        }
        if (value instanceof BsonDocument) {
            return needsProcessingValue(value.toBsonDocument());
        }
        throw new IllegalStateException("Unrecognized value: " + value);
    }

    private boolean needsProcessing(@Nullable List<Bson> values) {
        if (values == null) {
            return false;
        }
        for (Bson value : values) {
            if (needsProcessing(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean needsProcessingValue(BsonValue value) {
        if (value instanceof BsonDocument bsonDocument) {
            BsonInt32 queryParameterIndex = bsonDocument.getInt32(MongoQueryBuilder.QUERY_PARAMETER_PLACEHOLDER, null);
            if (queryParameterIndex != null) {
                return true;
            }
            for (Map.Entry<String, BsonValue> entry : bsonDocument.entrySet()) {
                BsonValue bsonValue = entry.getValue();
                if (needsProcessingValue(bsonValue)) {
                    return true;
                }
            }
            return false;
        }
        if (value instanceof BsonArray bsonArray) {
            for (BsonValue bsonValue : bsonArray) {
                if (needsProcessingValue(bsonValue)) {
                    return true;
                }
            }
        }
        if (value instanceof BsonRegularExpression bsonRegularExpression) {
            String pattern = bsonRegularExpression.getPattern();
            if (MONGO_PARAM_PATTERN.matcher(pattern).matches()) {
                return true;
            }
            return bsonRegularExpression.getOptions().contains("l");
        }
        return false;
    }

    private Bson replaceQueryParameters(Bson value, @Nullable InvocationContext<?, ?> invocationContext, @Nullable E entity) {
        if (value instanceof BsonDocument bsonDocument) {
            return (BsonDocument) replaceQueryParametersInBsonValue(bsonDocument.clone(), invocationContext, entity);
        }
        throw new IllegalStateException("Unrecognized value: " + value);
    }

    private List<Bson> replaceQueryParametersInList(List<Bson> values, @Nullable InvocationContext<?, ?> invocationContext, @Nullable E entity) {
        values = new ArrayList<>(values);
        for (int i = 0; i < values.size(); i++) {
            Bson value = values.get(i);
            Bson newValue = replaceQueryParameters(value, invocationContext, entity);
            if (value != newValue) {
                values.set(i, newValue);
            }
        }
        return values;
    }

    private Map.Entry<QueryParameterBinding, Object> bind(QueryParameterBinding queryParameterBinding, @Nullable InvocationContext<?, ?> invocationContext, @Nullable E entity) {
        Object[] holder = new Object[1];
        bindParameter(new Binder() {

            @Override
            public Object autoPopulateRuntimeProperty(RuntimePersistentProperty<?> persistentProperty, @Nullable Object previousValue) {
                return runtimeEntityRegistry.autoPopulateRuntimeProperty(persistentProperty, previousValue);
            }

            @Override
            @Nullable
            public Object convert(@Nullable Object value, @Nullable RuntimePersistentProperty<?> property) {
                if (property != null) {
                    AttributeConverter<Object, Object> converter = property.getConverter();
                    if (converter != null) {
                        return converter.convertToPersistedValue(value, createTypeConversionContext(property, property.getArgument()));
                    }
                }
                return value;
            }

            @Nullable
            @Override
            public Object convert(@Nullable Class<?> converterClass, @Nullable Object value, @Nullable Argument<?> argument) {
                if (converterClass == null) {
                    return value;
                }
                AttributeConverter<Object, Object> converter = attributeConverterRegistry.getConverter(converterClass);
                ConversionContext conversionContext = createTypeConversionContext(null, argument);
                return converter.convertToPersistedValue(value, conversionContext);
            }

            private ConversionContext createTypeConversionContext(@Nullable RuntimePersistentProperty<?> property, @Nullable Argument<?> argument) {
                if (argument != null) {
                    return ConversionContext.of(argument);
                }
                if (property != null) {
                    return ConversionContext.of(property.getArgument());
                }
                return ConversionContext.DEFAULT;
            }

            @Override
            public void bindOne(QueryParameterBinding binding, @Nullable Object value) {
                holder[0] = new AbstractMap.SimpleEntry<>(binding, value);
            }

            @Override
            public void bindMany(QueryParameterBinding binding, Collection<Object> values) {
                bindOne(binding, values);
            }

        }, invocationContext, entity, null, queryParameterBinding);
        return (Map.Entry<QueryParameterBinding, Object>) holder[0];
    }

    private BsonValue replaceQueryParametersInBsonValue(BsonValue value, @Nullable InvocationContext<?, ?> invocationContext, @Nullable E entity) {
        if (value instanceof BsonDocument bsonDocument) {
            BsonInt32 queryParameterIndex = bsonDocument.getInt32(MongoQueryBuilder.QUERY_PARAMETER_PLACEHOLDER, null);
            if (queryParameterIndex != null) {
                int index = queryParameterIndex.getValue();
                QueryParameterBinding queryParameterBinding = getQueryBindings().get(index);
                Map.Entry<QueryParameterBinding, Object> e = bind(queryParameterBinding, invocationContext, entity);
                if (e == null) {
                    throw new DataAccessException("Cannot bind a value at index: " + index);
                }
                BsonValue bsonValue = getValue(e.getKey(), e.getValue());
                if (bsonDocument.containsKey(MongoQueryBuilder.NEGATE)) {
                    if (bsonValue instanceof BsonDocumentWrapper<?> bsonDocumentWrapper) {
                        Object object = bsonDocumentWrapper.getWrappedDocument();
                        if (object instanceof Long aLong) {
                            return new BsonDocumentWrapper<>(
                                -aLong,
                                (Encoder<? super Long>) bsonDocumentWrapper.getEncoder()
                            );
                        }
                        if (object instanceof Integer integer) {
                            return new BsonDocumentWrapper<>(
                                -integer,
                                (Encoder<? super Integer>) bsonDocumentWrapper.getEncoder()
                            );
                        }
                        if (object instanceof Double aDouble) {
                            return new BsonDocumentWrapper<>(
                                -aDouble,
                                (Encoder<? super Double>) bsonDocumentWrapper.getEncoder()
                            );
                        }
                        if (object instanceof Float aFloat) {
                            return new BsonDocumentWrapper<>(
                                -aFloat,
                                (Encoder<? super Float>) bsonDocumentWrapper.getEncoder()
                            );
                        }
                    }
                    return switch (bsonValue.getBsonType()) {
                        case INT32 -> new BsonInt32(-bsonValue.asInt32().getValue());
                        case INT64 -> new BsonInt64(-bsonValue.asInt64().getValue());
                        case DOUBLE -> new BsonDouble(-bsonValue.asDouble().getValue());
                        default -> bsonValue;
                    };
                }
                if (bsonDocument.containsKey(MongoQueryBuilder.RECIPROCATE)) {
                    if (bsonValue instanceof BsonDocumentWrapper<?> bsonDocumentWrapper) {
                        Object object = bsonDocumentWrapper.getWrappedDocument();
                        if (object instanceof Long aLong) {
                            return new BsonDocumentWrapper<>(
                                1L / aLong,
                                (Encoder<? super Long>) bsonDocumentWrapper.getEncoder()
                            );
                        }
                        if (object instanceof Integer integer) {
                            return new BsonDocumentWrapper<>(
                                1 / integer,
                                (Encoder<? super Integer>) bsonDocumentWrapper.getEncoder()
                            );
                        }
                        if (object instanceof Double aDouble) {
                            return new BsonDocumentWrapper<>(
                                1D / aDouble,
                                (Encoder<? super Double>) bsonDocumentWrapper.getEncoder()
                            );
                        }
                        if (object instanceof Float aFloat) {
                            return new BsonDocumentWrapper<>(
                                1F / aFloat,
                                (Encoder<? super Float>) bsonDocumentWrapper.getEncoder()
                            );
                        }
                    }
                    return switch (bsonValue.getBsonType()) {
                        case INT32 -> new BsonInt32(1 / bsonValue.asInt32().getValue());
                        case INT64 -> new BsonInt64(1L / bsonValue.asInt64().getValue());
                        case DOUBLE -> new BsonDouble(1D / bsonValue.asDouble().getValue());
                        default -> bsonValue;
                    };
                }
                return bsonValue;
            }
            for (Map.Entry<String, BsonValue> entry : bsonDocument.entrySet()) {
                BsonValue bsonValue = entry.getValue();
                BsonValue newValue = replaceQueryParametersInBsonValue(bsonValue, invocationContext, entity);
                if (bsonValue != newValue) {
                    entry.setValue(newValue);
                }
            }
            return bsonDocument;
        } else if (value instanceof BsonArray bsonArray) {
            for (int i = 0; i < bsonArray.size(); i++) {
                BsonValue bsonValue = bsonArray.get(i);
                BsonValue newValue = replaceQueryParametersInBsonValue(bsonValue, invocationContext, entity);
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
        } else if (value instanceof BsonRegularExpression bsonRegularExpression) {
            String pattern = bsonRegularExpression.getPattern();
            Matcher matcher = MONGO_PARAM_PATTERN.matcher(pattern);
            String originalOptions = bsonRegularExpression.getOptions();
            String sanitizedOptions = originalOptions.contains("l") ? originalOptions.replace("l", "") : originalOptions;
            if (!sanitizedOptions.equals(originalOptions) && !matcher.matches()) {
                return new BsonRegularExpression(pattern, sanitizedOptions);
            }
            if (matcher.matches()) {
                Integer queryParamIndex = null;
                try {
                    String queryParamIndexStr = matcher.group(2);
                    queryParamIndex = Integer.parseInt(queryParamIndexStr);
                } catch (Exception e) {
                    LOG.info("Failed to get mongo parameter for regex {}", pattern, e);
                }
                if (queryParamIndex != null) {
                    QueryParameterBinding queryParameterBinding = getQueryBindings().get(queryParamIndex);
                    Map.Entry<QueryParameterBinding, Object> e = bind(queryParameterBinding, invocationContext, entity);
                    if (e == null) {
                        throw new DataAccessException("Cannot bind a value at index: " + queryParamIndex);
                    }
                    pattern = pattern.replace(matcher.group(1), e.getValue().toString());
                    if (originalOptions.contains("l")) {
                        pattern = pattern
                            .replace("_", ".")
                            .replace("%", ".*");
                        if (!pattern.startsWith("^")) {
                            pattern = "^" + pattern;
                        }
                        if (!pattern.endsWith("$")) {
                            pattern = pattern + "$";
                        }
                    }
                    return new BsonRegularExpression(pattern, sanitizedOptions);
                }
            }
        }
        return value;
    }

    private BsonValue getValue(QueryParameterBinding queryParameterBinding, Object value) {
        // Check if the parameter is not an id which might be represented as String but needs to mapped as ObjectId
        boolean isIdentity = false;
        // TODO: improve id recognition
        if (queryParameterBinding.getPropertyPath() != null) {
            PersistentPropertyPath pp = getRequiredPropertyPath(queryParameterBinding, persistentEntity);
            RuntimePersistentProperty<?> persistentProperty = (RuntimePersistentProperty) pp.getProperty();
            if (persistentProperty instanceof RuntimeAssociation runtimeAssociation) {
                if (runtimeAssociation.getAssociatedEntity().hasIdentity()) {
                    RuntimePersistentProperty identity = runtimeAssociation.getAssociatedEntity().getIdentity();
                    isIdentity = identity.getType() == String.class && identity.isGenerated();
                }
            } else {
                isIdentity = persistentProperty.getOwner().hasIdentity() && persistentProperty.getOwner().getIdentity() == persistentProperty && persistentProperty.getType() == String.class && persistentProperty.isGenerated();
            }
        }

        if (isIdentity && value instanceof String) {
            return new BsonObjectId(new ObjectId((String) value));
        }
        if (value instanceof Object[] objects) {
            List<Object> valueList = Arrays.asList(objects);
            if (isIdentity) {
                for (ListIterator<Object> iterator = valueList.listIterator(); iterator.hasNext(); ) {
                    Object item = iterator.next();
                    if (item instanceof String string) {
                        item = new BsonObjectId(new ObjectId(string));
                    }
                    iterator.set(item);
                }
            }
            value = valueList;
        }
        if (value instanceof Collection<?> values) {
            final boolean isIdentityField = isIdentity;
            return new BsonArray(values.stream().map(val -> {
                if (isIdentityField && val instanceof String string) {
                    return new BsonObjectId(new ObjectId(string));
                }
                return MongoUtils.toBsonValue(val, codecRegistry.get());
            }).toList());
        }
        return MongoUtils.toBsonValue(value, codecRegistry.get());
    }

    @Override
    public StoredQuery<E, R> getStoredQueryDelegate() {
        return storedQuery;
    }

    private final class AggregateData extends CollationSupported {
        @Nullable
        private final List<Bson> pipeline;
        private final boolean pipelineNeedsProcessing;
        @Nullable
        private final MongoAggregationOptions options;
        private final int pipelineParameterIndex;
        private final int optionsParameterIndex;

        private AggregateData(@Nullable List<Bson> pipeline) {
            this(pipeline, null, null);
        }

        private AggregateData(@Nullable String pipelineParameter, @Nullable String optionsParameter) {
            this(null, pipelineParameter, optionsParameter);
        }

        private AggregateData(@Nullable List<Bson> pipeline, @Nullable String pipelineParameter, @Nullable String optionsParameter) {
            this.pipeline = pipeline;
            this.pipelineParameterIndex = getParameterIndexByName(pipelineParameter);
            this.optionsParameterIndex = getParameterIndexByName(optionsParameter);
            this.pipelineNeedsProcessing = needsProcessing(pipeline);
            options = MongoOptionsUtils.buildAggregateOptions(storedQuery.getAnnotationMetadata()).orElse(null);
        }

        public MongoAggregation getAggregation(InvocationContext<?, ?> invocationContext) {
            List<Bson> pipeline = getPipeline(invocationContext);
            MongoAggregationOptions options = getOptions(invocationContext);
            Collation collation = getCollation(invocationContext, null);
            if (collation != null) {
                if (options == null) {
                    options = new MongoAggregationOptions();
                }
                options.collation(collation);
            }
            return new MongoAggregation(Objects.requireNonNull(pipeline), options);
        }

        @Nullable
        private List<Bson> getPipeline(InvocationContext<?, ?> invocationContext) {
            if (pipelineParameterIndex != -1) {
                return getParameterAtIndex(invocationContext, pipelineParameterIndex);
            }
            if (pipelineNeedsProcessing) {
                return replaceQueryParametersInList(Objects.requireNonNull(pipeline), invocationContext, null);
            }
            return pipeline;
        }

        @Nullable
        private MongoAggregationOptions getOptions(InvocationContext<?, ?> invocationContext) {
            if (optionsParameterIndex != -1) {
                MongoAggregationOptions paramOptions = getParameterAtIndex(invocationContext, optionsParameterIndex);
                if (this.options == null) {
                    return paramOptions;
                } else if (paramOptions != null) {
                    MongoAggregationOptions options = new MongoAggregationOptions(this.options);
                    options.copyNotNullFrom(paramOptions);
                    return options;
                }
            }
            return this.options;
        }

    }

    private final class UpdateData extends CollationSupported {
        @Nullable
        private final Bson update;
        private final boolean updateNeedsProcessing;
        @Nullable
        private final Bson filter;
        private final boolean filterNeedsProcessing;
        @Nullable
        private final UpdateOptions options;
        private final int filterParameterIndex;
        private final int updateParameterIndex;
        private final int optionsParameterIndex;

        private UpdateData(@Nullable Bson update, @Nullable Bson filter, @Nullable String filterParameter, @Nullable String updateParameter, @Nullable String optionsParameter) {
            this.update = update;
            this.updateNeedsProcessing = needsProcessing(update);
            this.filter = filter;
            this.filterNeedsProcessing = needsProcessing(filter);
            this.filterParameterIndex = getParameterIndexByName(filterParameter);
            this.updateParameterIndex = getParameterIndexByName(updateParameter);
            this.optionsParameterIndex = getParameterIndexByName(optionsParameter);
            this.options = MongoOptionsUtils.buildUpdateOptions(storedQuery.getAnnotationMetadata(), false).orElse(null);
        }

        private UpdateOptions copy(UpdateOptions options) {
            UpdateOptions newOptions = new UpdateOptions();
            newOptions.collation(options.getCollation());
            newOptions.upsert(options.isUpsert());
            newOptions.bypassDocumentValidation(options.getBypassDocumentValidation());
            newOptions.hint(options.getHint());
            newOptions.hintString(options.getHintString());
            newOptions.arrayFilters(options.getArrayFilters());
            return newOptions;
        }

        private void copyNonNullFrom(UpdateOptions to, UpdateOptions from) {
            if (from.getCollation() != null) {
                to.collation(from.getCollation());
            }
            if (from.isUpsert()) {
                to.upsert(from.isUpsert());
            }
            if (from.getBypassDocumentValidation() != null) {
                to.bypassDocumentValidation(from.getBypassDocumentValidation());
            }
            if (from.getHint() != null) {
                to.hint(from.getHint());
            }
            if (from.getHintString() != null) {
                to.hintString(from.getHintString());
            }
            if (from.getArrayFilters() != null) {
                to.arrayFilters(from.getArrayFilters());
            }
        }

        private MongoUpdate getUpdateMany(InvocationContext<?, ?> invocationContext) {
            return new MongoUpdate(
                getUpdate(invocationContext, null),
                getFilter(invocationContext, null),
                getOptions(invocationContext));
        }

        private MongoUpdate getUpdateOne(E entity) {
            if (updateData == null) {
                throw new IllegalStateException("Expected update query!");
            }
            Bson update = getUpdate(null, entity);
            UpdateOptions options = getOptions(null);
            return new MongoUpdate(update, getFilter(null, entity), options);
        }

        private Bson getUpdate(@Nullable InvocationContext<?, ?> invocationContext, @Nullable E entity) {
            Bson update = this.update;
            if (updateParameterIndex != -1) {
                update = getParameterAtIndex(invocationContext, updateParameterIndex);
            }
            if (update == null) {
                throw new IllegalStateException("Update query is not provided!");
            }
            update = updateNeedsProcessing ? replaceQueryParameters(update, invocationContext, entity) : update;
            if (update == null) {
                throw new IllegalStateException("Update query is not provided!");
            }
            return update;
        }

        @NonNull
        private UpdateOptions getOptions(@Nullable InvocationContext<?, ?> invocationContext) {
            UpdateOptions options = this.options;
            if (optionsParameterIndex != -1) {
                UpdateOptions paramOptions = getParameterAtIndex(invocationContext, optionsParameterIndex);
                if (paramOptions != null) {
                    if (options == null) {
                        options = paramOptions;
                    } else {
                        options = copy(options);
                        copyNonNullFrom(options, paramOptions);
                    }
                }
            }
            if (options == null) {
                options = new UpdateOptions();
            }
            Collation collation = getCollation(invocationContext, null);
            if (collation != null) {
                if (options == this.options) {
                    options = copy(options);
                }
                options.collation(collation);
            }
            return options;
        }

        @Nullable
        private Bson getFilter(@Nullable InvocationContext<?, ?> invocationContext, @Nullable E entity) {
            if (filterParameterIndex != -1) {
                return getParameterAtIndex(invocationContext, filterParameterIndex);
            }
            return filterNeedsProcessing ? replaceQueryParameters(Objects.requireNonNull(filter), invocationContext, entity) : filter;
        }
    }

    private final class UpdateReturningData extends CollationSupported {
        @Nullable
        private final Bson update;
        private final boolean updateNeedsProcessing;
        @Nullable
        private final Bson filter;
        private final boolean filterNeedsProcessing;
        @Nullable
        private final FindOneAndUpdateOptions options;
        @Nullable
        private final Bson projection;
        private final boolean projectionNeedsProcessing;
        @Nullable
        private final Bson sort;
        private final boolean sortNeedsProcessing;
        private final int filterParameterIndex;
        private final int updateParameterIndex;
        private final int optionsParameterIndex;

        private UpdateReturningData(@Nullable Bson update, @Nullable Bson filter, @Nullable String filterParameter, @Nullable String updateParameter,
                                    @Nullable String optionsParameter) {
            this.update = update;
            this.updateNeedsProcessing = needsProcessing(update);
            this.filter = filter;
            this.filterNeedsProcessing = needsProcessing(filter);
            this.filterParameterIndex = getParameterIndexByName(filterParameter);
            this.updateParameterIndex = getParameterIndexByName(updateParameter);
            this.optionsParameterIndex = getParameterIndexByName(optionsParameter);
            this.projection = storedQuery.getAnnotationMetadata()
                    .stringValue(MongoProjection.class)
                    .map(json -> parseProjectionOrSortJson(json, "MongoProjection"))
                    .orElse(null);
            this.projectionNeedsProcessing = needsProcessing(this.projection);
            this.sort = storedQuery.getAnnotationMetadata()
                    .stringValue(MongoSort.class)
                    .map(json -> parseProjectionOrSortJson(json, "MongoSort"))
                    .orElse(null);
            this.sortNeedsProcessing = needsProcessing(this.sort);
            this.options = MongoOptionsUtils.buildFindOneAndUpdateOptions(storedQuery.getAnnotationMetadata(), false).orElse(null);
        }

        /**
         * Parse BSON JSON for MongoProjection/MongoSort annotations.
         * <p>
         * Projection and sort definitions are expected to be constant JSON. Using
         * {@code :param}-style placeholders is not supported and will result in
         * a {@link DataAccessException}.
         *
         * @param json         The raw JSON from the annotation.
         * @param annotationId The annotation identifier (for error messages).
         * @return The parsed {@link BsonDocument}.
         */
        @NonNull
        private BsonDocument parseProjectionOrSortJson(@NonNull String json, @NonNull String annotationId) {
            try {
                return BsonDocument.parse(json);
            } catch (RuntimeException e) {
                throw new DataAccessException(
                        "Failed to parse " + annotationId + " JSON. " +
                        annotationId + " must contain constant JSON and does not support :param-style placeholders. " +
                        "Invalid value: " + json,
                        e
                );
            }
        }
        private FindOneAndUpdateOptions copy(FindOneAndUpdateOptions options) {
            FindOneAndUpdateOptions newOptions = new FindOneAndUpdateOptions();
            newOptions.collation(options.getCollation());
            newOptions.upsert(options.isUpsert());
            newOptions.bypassDocumentValidation(options.getBypassDocumentValidation());
            newOptions.hint(options.getHint());
            newOptions.hintString(options.getHintString());
            newOptions.arrayFilters(options.getArrayFilters());
            newOptions.returnDocument(options.getReturnDocument());
            newOptions.projection(options.getProjection());
            newOptions.sort(options.getSort());
            return newOptions;
        }

        private void copyNonNullFrom(FindOneAndUpdateOptions to, FindOneAndUpdateOptions from) {
            if (from.getCollation() != null) {
                to.collation(from.getCollation());
            }
            if (from.isUpsert()) {
                to.upsert(from.isUpsert());
            }
            if (from.getBypassDocumentValidation() != null) {
                to.bypassDocumentValidation(from.getBypassDocumentValidation());
            }
            if (from.getHint() != null) {
                to.hint(from.getHint());
            }
            if (from.getHintString() != null) {
                to.hintString(from.getHintString());
            }
            if (from.getArrayFilters() != null) {
                to.arrayFilters(from.getArrayFilters());
            }
            if (from.getReturnDocument() != null && to.getReturnDocument() == null) {
                to.returnDocument(from.getReturnDocument());
            }
            if (from.getProjection() != null) {
                to.projection(from.getProjection());
            }
            if (from.getSort() != null) {
                to.sort(from.getSort());
            }
        }

        private MongoFindOneAndUpdate getUpdateOne(InvocationContext<?, ?> invocationContext) {
            return new MongoFindOneAndUpdate(
                getUpdate(invocationContext, null),
                getFilter(invocationContext, null),
                getOptions(invocationContext));
        }

        private Bson getUpdate(@Nullable InvocationContext<?, ?> invocationContext, @Nullable E entity) {
            Bson update = this.update;
            if (updateParameterIndex != -1) {
                update = getParameterAtIndex(invocationContext, updateParameterIndex);
            }
            if (update == null) {
                throw new IllegalStateException("Update query is not provided!");
            }
            update = updateNeedsProcessing ? replaceQueryParameters(update, invocationContext, entity) : update;
            if (update == null) {
                throw new IllegalStateException("Update query is not provided!");
            }
            return update;
        }

        @NonNull
        private FindOneAndUpdateOptions getOptions(@Nullable InvocationContext<?, ?> invocationContext) {
            FindOneAndUpdateOptions options = this.options;
            if (optionsParameterIndex != -1) {
                FindOneAndUpdateOptions paramOptions = getParameterAtIndex(invocationContext, optionsParameterIndex);
                if (paramOptions != null) {
                    if (options == null) {
                        options = paramOptions;
                    } else {
                        options = copy(options);
                        copyNonNullFrom(options, paramOptions);
                    }
                }
            }
            if (options == null) {
                options = new FindOneAndUpdateOptions();
            }
            Collation collation = getCollation(invocationContext, null);
            if (collation != null) {
                if (options == this.options) {
                    options = copy(options);
                }
                options.collation(collation);
            }
            Bson projection = getProjection(invocationContext, null);
            if (projection != null) {
                if (options == this.options) {
                    options = copy(options);
                }
                options.projection(projection);
            }
            Bson sort = getSort(invocationContext, null);
            if (sort != null) {
                if (options == this.options) {
                    options = copy(options);
                }
                options.sort(sort);
            }
            return options;
        }

        @Nullable
        private Bson getFilter(@Nullable InvocationContext<?, ?> invocationContext, @Nullable E entity) {
            if (filterParameterIndex != -1) {
                return getParameterAtIndex(invocationContext, filterParameterIndex);
            }
            return filterNeedsProcessing ? replaceQueryParameters(Objects.requireNonNull(filter), invocationContext, entity) : filter;
        }

        @Nullable
        private Bson getProjection(@Nullable InvocationContext<?, ?> invocationContext, @Nullable E entity) {
            if (projection == null) {
                return null;
            }
            return projectionNeedsProcessing ? replaceQueryParameters(projection, invocationContext, entity) : projection;
        }

        @Nullable
        private Bson getSort(@Nullable InvocationContext<?, ?> invocationContext, @Nullable E entity) {
            if (sort == null) {
                return null;
            }
            return sortNeedsProcessing ? replaceQueryParameters(sort, invocationContext, entity) : sort;
        }
    }

    private final class FindData extends CollationSupported {
        @Nullable
        private final Bson filter;
        private final boolean filterNeedsProcessing;
        @Nullable
        private final Bson sort;
        private final boolean sortNeedsProcessing;
        @Nullable
        private final Bson projection;
        private final boolean projectionNeedsProcessing;
        @Nullable
        private final MongoFindOptions options;
        private final int filterParameterIndex;
        private final int optionsParameterIndex;

        private FindData(@Nullable Bson filter) {
            this(filter, null, null);
        }

        private FindData(@Nullable String filterParameter, @Nullable String optionsParameter) {
            this(null, filterParameter, optionsParameter);
        }

        private FindData(@Nullable Bson filter, @Nullable String filterParameter, @Nullable String optionsParameter) {
            this.filterParameterIndex = getParameterIndexByName(filterParameter);
            this.optionsParameterIndex = getParameterIndexByName(optionsParameter);
            sort = storedQuery.getAnnotationMetadata().stringValue(MongoSort.class).map(BsonDocument::parse).orElse(null);
            sortNeedsProcessing = needsProcessing(sort);
            projection = storedQuery.getAnnotationMetadata().stringValue(MongoProjection.class).map(BsonDocument::parse).orElse(null);
            projectionNeedsProcessing = needsProcessing(projection);
            this.filter = filter;
            this.filterNeedsProcessing = needsProcessing(filter);
            options = MongoOptionsUtils.buildFindOptions(storedQuery.getAnnotationMetadata()).orElse(null);
        }

        private MongoFind getFind(InvocationContext<?, ?> invocationContext) {
            MongoFindOptions options = getFilterOptions(invocationContext);
            Bson filter = getFilter(invocationContext, null);
            if (filter != null) {
                options.filter(filter);
            }
            Collation collation = getCollation(invocationContext, null);
            if (collation != null) {
                options.collation(collation);
            }
            Bson sort = getSort(invocationContext, null);
            if (sort != null) {
                options.sort(sort);
            }
            Bson projection = getProjection(invocationContext, null);
            if (projection != null) {
                options.projection(projection);
            }
            return new MongoFind(options.isEmpty() ? null : options);
        }

        @NonNull
        private MongoFindOptions getFilterOptions(@Nullable InvocationContext<?, ?> invocationContext) {
            if (optionsParameterIndex != -1) {
                MongoFindOptions paramOptions = getParameterAtIndex(invocationContext, optionsParameterIndex);
                if (paramOptions != null) {
                    if (options == null) {
                        return paramOptions;
                    }
                    MongoFindOptions options = new MongoFindOptions(this.options);
                    options.copyNotNullFrom(paramOptions);
                    return options;
                }
            }
            if (options != null) {
                return new MongoFindOptions(options);
            }
            return new MongoFindOptions();
        }

        @Nullable
        private Bson getFilter(@Nullable InvocationContext<?, ?> invocationContext, @Nullable E entity) {
            if (filterParameterIndex != -1) {
                return getParameterAtIndex(invocationContext, filterParameterIndex);
            }
            if (filter == null) {
                return null;
            }
            return filterNeedsProcessing ? replaceQueryParameters(filter, invocationContext, entity) : filter;
        }

        @Nullable
        private Bson getSort(@Nullable InvocationContext<?, ?> invocationContext, @Nullable E entity) {
            if (sort == null) {
                return null;
            }
            return sortNeedsProcessing ? replaceQueryParameters(sort, invocationContext, entity) : sort;
        }

        @Nullable
        private Bson getProjection(@Nullable InvocationContext<?, ?> invocationContext, @Nullable E entity) {
            if (projection == null) {
                return null;
            }
            return projectionNeedsProcessing ? replaceQueryParameters(projection, invocationContext, entity) : projection;
        }

    }

    private final class DeleteData extends CollationSupported {
        private final Bson filter;
        private final boolean filterNeedsProcessing;
        @Nullable
        private final DeleteOptions options;
        private final int filterParameterIndex;
        private final int optionsParameterIndex;

        private DeleteData(@Nullable Bson filter, @Nullable String filterParameter, @Nullable String optionsParameter) {
            this.filter = filter == null ? new BsonDocument() : filter;
            this.filterNeedsProcessing = needsProcessing(filter);
            this.filterParameterIndex = getParameterIndexByName(filterParameter);
            this.optionsParameterIndex = getParameterIndexByName(optionsParameter);
            options = MongoOptionsUtils.buildDeleteOptions(storedQuery.getAnnotationMetadata(), false).orElse(null);
        }

        private MongoDelete getDeleteMany(@Nullable InvocationContext<?, ?> invocationContext) {
            DeleteOptions options = getOptions(invocationContext);
            return new MongoDelete(getFilter(invocationContext, null), options);
        }

        private MongoDelete getDeleteOne(E entity) {
            DeleteOptions options = getOptions(null);
            return new MongoDelete(getFilter(null, entity), options);
        }

        @NonNull
        private DeleteOptions getOptions(@Nullable InvocationContext<?, ?> invocationContext) {
            DeleteOptions options = this.options;
            if (optionsParameterIndex != -1) {
                DeleteOptions paramOptions = getParameterAtIndex(invocationContext, optionsParameterIndex);
                if (paramOptions != null) {
                    if (options == null) {
                        options = paramOptions;
                    } else {
                        options = copy(options);
                        copyNonNullFrom(options, paramOptions);
                    }
                }
            }
            if (options == null) {
                options = new DeleteOptions();
            }
            Collation collation = getCollation(invocationContext, null);
            if (collation != null) {
                if (this.options == options) {
                    options = copy(options);
                }
                options.collation(collation);
            }
            return options;
        }

        private DeleteOptions copy(DeleteOptions options) {
            DeleteOptions newOptions = new DeleteOptions();
            newOptions.collation(options.getCollation());
            newOptions.hint(options.getHint());
            newOptions.hintString(options.getHintString());
            return newOptions;
        }

        private void copyNonNullFrom(DeleteOptions to, DeleteOptions from) {
            if (from.getCollation() != null) {
                to.collation(from.getCollation());
            }
            if (from.getHint() != null) {
                to.hint(from.getHint());
            }
            if (from.getHintString() != null) {
                to.hintString(from.getHintString());
            }
        }

        @Nullable
        private Bson getFilter(@Nullable InvocationContext<?, ?> invocationContext, @Nullable E entity) {
            if (filterParameterIndex != -1) {
                return getParameterAtIndex(invocationContext, filterParameterIndex);
            }
            return filterNeedsProcessing ? replaceQueryParameters(filter, invocationContext, entity) : filter;
        }
    }

    private abstract class CollationSupported {
        @Nullable
        private final Bson collationAsBson;
        private final boolean collationNeedsProcessing;
        @Nullable
        private final Collation collation;

        private CollationSupported() {
            collationAsBson = storedQuery.getAnnotationMetadata().stringValue(MongoCollation.class).map(BsonDocument::parse).orElse(null);
            collationNeedsProcessing = needsProcessing(collationAsBson);
            collation = collationAsBson == null || collationNeedsProcessing ? null : MongoOptionsUtils.bsonDocumentAsCollation(collationAsBson.toBsonDocument());
        }

        @Nullable
        protected Collation getCollation(@Nullable InvocationContext<?, ?> invocationContext, @Nullable E entity) {
            if (collation != null) {
                return collation;
            }
            if (collationAsBson == null) {
                return null;
            }
            Bson collationAsBson = collationNeedsProcessing ? replaceQueryParameters(this.collationAsBson, invocationContext, entity) : this.collationAsBson;
            return MongoOptionsUtils.bsonDocumentAsCollation(collationAsBson.toBsonDocument());
        }
    }
}
