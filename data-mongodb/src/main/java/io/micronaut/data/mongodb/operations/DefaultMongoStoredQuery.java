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
import com.mongodb.client.model.UpdateOptions;
import io.micronaut.aop.InvocationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.document.model.query.builder.MongoQueryBuilder;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.DataType;
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
import io.micronaut.data.runtime.query.internal.DefaultStoredQuery;
import io.micronaut.data.runtime.query.internal.DelegateStoredQuery;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonObjectId;
import org.bson.BsonValue;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link MongoStoredQuery}.
 *
 * @param <E>   The entity type
 * @param <R>   The result type
 * @param <Dtb> The database type
 * @author Denis Stepanov
 * @since 3.3.
 */
@Internal
final class DefaultMongoStoredQuery<E, R, Dtb> implements DelegateStoredQuery<E, R>, MongoStoredQuery<E, R, Dtb> {

    private static final BsonDocument EMPTY = new BsonDocument();

    private final StoredQuery<E, R> storedQuery;
    private final CodecRegistry codecRegistry;
    private final AttributeConverterRegistry attributeConverterRegistry;
    private final RuntimeEntityRegistry runtimeEntityRegistry;
    private final ConversionService<?> conversionService;
    private final Dtb database;
    private final RuntimePersistentEntity<E> persistentEntity;
    private final UpdateData updateData;
    private final FindData findData;
    private final AggregateData aggregateData;
    private final DeleteData deleteData;
    private final boolean isCount;

    DefaultMongoStoredQuery(StoredQuery<E, R> storedQuery,
                            CodecRegistry codecRegistry,
                            AttributeConverterRegistry attributeConverterRegistry,
                            RuntimeEntityRegistry runtimeEntityRegistry,
                            ConversionService<?> conversionService,
                            RuntimePersistentEntity<E> persistentEntity,
                            Dtb database) {
        this(storedQuery,
                codecRegistry,
                attributeConverterRegistry,
                runtimeEntityRegistry,
                conversionService,
                persistentEntity,
                database,
                storedQuery.getAnnotationMetadata().enumValue(DataMethod.NAME, DataMethod.META_MEMBER_OPERATION_TYPE, DataMethod.OperationType.class)
                        .orElseThrow(IllegalStateException::new),
                storedQuery.getAnnotationMetadata().stringValue(Query.class, "update").orElse(null));
    }

    DefaultMongoStoredQuery(StoredQuery<E, R> storedQuery,
                            CodecRegistry codecRegistry,
                            AttributeConverterRegistry attributeConverterRegistry,
                            RuntimeEntityRegistry runtimeEntityRegistry,
                            ConversionService<?> conversionService,
                            RuntimePersistentEntity<E> persistentEntity,
                            Dtb database,
                            DataMethod.OperationType operationType,
                            String updateJson) {
        this.storedQuery = storedQuery;
        this.codecRegistry = codecRegistry;
        this.attributeConverterRegistry = attributeConverterRegistry;
        this.runtimeEntityRegistry = runtimeEntityRegistry;
        this.conversionService = conversionService;
        this.database = database;
        this.persistentEntity = persistentEntity;
        if (operationType == DataMethod.OperationType.QUERY || operationType == DataMethod.OperationType.EXISTS || operationType == DataMethod.OperationType.COUNT) {
            String query = storedQuery.getQuery();
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
                aggregateData = new AggregateData(BsonArray.parse(query).stream().map(BsonValue::asDocument).collect(Collectors.toList()));
                findData = null;
            } else {
                aggregateData = null;
                findData = new FindData(BsonDocument.parse(query));
            }
            isCount = operationType == DataMethod.OperationType.COUNT || storedQuery.isCount() || query.contains("$count");
        } else {
            aggregateData = null;
            findData = null;
            isCount = false;
        }

        if (operationType == DataMethod.OperationType.DELETE) {
            String query = storedQuery.getQuery();
            deleteData = new DeleteData(
                    StringUtils.isEmpty(query) ? EMPTY : BsonDocument.parse(query),
                    getParameterInRole(MongoRoles.FILTER_ROLE),
                    getParameterInRole(MongoRoles.DELETE_OPTIONS_ROLE)
            );
        } else {
            deleteData = null;
        }

        if (operationType == DataMethod.OperationType.UPDATE) {
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
    }

    @Override
    public boolean isCount() {
        return isCount;
    }

    @Nullable
    private String getParameterInRole(String role) {
        if (storedQuery instanceof DefaultStoredQuery) {
            return storedQuery.getAnnotationMetadata().getAnnotation(DataMethod.class).stringValue(role).orElse(null);
        }
        return null;
    }

    @Nullable
    private int getParameterIndexByName(@Nullable String name) {
        if (name == null) {
            return -1;
        }
        if (storedQuery instanceof DefaultStoredQuery) {
            String[] argumentNames = ((DefaultStoredQuery<E, R>) storedQuery).getMethod().getArgumentNames();
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
    private <X> X getParameterAtIndex(InvocationContext<?, ?> invocationContext, int index) {
        requireInvocationContext(invocationContext);
        return (X) invocationContext.getParameterValues()[index];
    }

    @Override
    public RuntimePersistentEntity<E> getRuntimePersistentEntity() {
        return persistentEntity;
    }

    @Override
    public Dtb getDatabase() {
        return database;
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

    private boolean needsProcessing(Bson value) {
        if (value == null) {
            return false;
        }
        if (value instanceof BsonDocument) {
            return needsProcessingValue(value.toBsonDocument());
        }
        throw new IllegalStateException("Unrecognized value: " + value);
    }

    private boolean needsProcessing(List<Bson> values) {
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
        if (value instanceof BsonDocument) {
            BsonDocument bsonDocument = (BsonDocument) value;
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
        } else if (value instanceof BsonArray) {
            BsonArray bsonArray = (BsonArray) value;
            for (BsonValue bsonValue : bsonArray) {
                if (needsProcessingValue(bsonValue)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Bson replaceQueryParameters(Bson value, @Nullable InvocationContext<?, ?> invocationContext, @Nullable E entity) {
        if (value instanceof BsonDocument) {
            return (BsonDocument) replaceQueryParametersInBsonValue(((BsonDocument) value).clone(), invocationContext, entity);
        }
        throw new IllegalStateException("Unrecognized value: " + value);
    }

    private <T> List<Bson> replaceQueryParametersInList(List<Bson> values, @Nullable InvocationContext<?, ?> invocationContext, @Nullable E entity) {
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

    private BsonValue replaceQueryParametersInBsonValue(BsonValue value, @Nullable InvocationContext<?, ?> invocationContext, @Nullable E entity) {
        if (value instanceof BsonDocument) {
            BsonDocument bsonDocument = (BsonDocument) value;
            BsonInt32 queryParameterIndex = bsonDocument.getInt32(MongoQueryBuilder.QUERY_PARAMETER_PLACEHOLDER, null);
            if (queryParameterIndex != null) {
                int index = queryParameterIndex.getValue();
                return getValue(index, getQueryBindings().get(index), invocationContext, persistentEntity, codecRegistry, entity);
            }
            for (Map.Entry<String, BsonValue> entry : bsonDocument.entrySet()) {
                BsonValue bsonValue = entry.getValue();
                BsonValue newValue = replaceQueryParametersInBsonValue(bsonValue, invocationContext, entity);
                if (bsonValue != newValue) {
                    entry.setValue(newValue);
                }
            }
            return bsonDocument;
        } else if (value instanceof BsonArray) {
            BsonArray bsonArray = (BsonArray) value;
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
        }
        return value;
    }

    private <QE, QR, T> BsonValue getValue(int index,
                                           QueryParameterBinding queryParameterBinding,
                                           InvocationContext<?, ?> invocationContext,
                                           RuntimePersistentEntity<T> persistentEntity,
                                           CodecRegistry codecRegistry, E entity) {
        Class<?> parameterConverter = queryParameterBinding.getParameterConverterClass();
        Object value;
        if (queryParameterBinding.getParameterIndex() != -1) {
            requireInvocationContext(invocationContext);
            value = resolveParameterValue(queryParameterBinding, invocationContext.getParameterValues());
        } else if (queryParameterBinding.isAutoPopulated()) {
            PersistentPropertyPath pp = getRequiredPropertyPath(queryParameterBinding, persistentEntity);
            RuntimePersistentProperty<?> persistentProperty = (RuntimePersistentProperty) pp.getProperty();
            Object previousValue = null;
            QueryParameterBinding previousPopulatedValueParameter = queryParameterBinding.getPreviousPopulatedValueParameter();
            if (previousPopulatedValueParameter != null) {
                if (previousPopulatedValueParameter.getParameterIndex() == -1) {
                    throw new IllegalStateException("Previous value parameter cannot be bind!");
                }
                previousValue = resolveParameterValue(previousPopulatedValueParameter, invocationContext.getParameterValues());
            }
            value = runtimeEntityRegistry.autoPopulateRuntimeProperty(persistentProperty, previousValue);
            value = convert(value, persistentProperty);
            parameterConverter = null;
        } else if (entity != null) {
            PersistentPropertyPath pp = getRequiredPropertyPath(queryParameterBinding, persistentEntity);
            value = pp.getPropertyValue(entity);
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
                Argument<?> argument;
                if (parameterIndex > -1) {
                    requireInvocationContext(invocationContext);
                    argument = invocationContext.getArguments()[parameterIndex];
                } else {
                    argument = null;
                }
                value = convert(parameterConverter, value, argument);
            }
            // Check if the parameter is not an id which might be represented as String but needs to mapped as ObjectId
            if (value instanceof String && queryParameterBinding.getPropertyPath() != null) {
                // TODO: improve id recognition
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
                    Argument<?> argument;
                    if (parameterIndex > -1) {
                        requireInvocationContext(invocationContext);
                        argument = invocationContext.getArguments()[parameterIndex];
                    } else {
                        argument = null;
                    }
                    val = convert(finalParameterConverter, val, argument);
                }
                return MongoUtils.toBsonValue(conversionService, val, codecRegistry);
            }).collect(Collectors.toList()));
        }
    }

    private void requireInvocationContext(InvocationContext<?, ?> invocationContext) {
        if (invocationContext == null) {
            throw new IllegalStateException("Invocation context is required!");
        }
    }

    private Object convert(Class<?> converterClass, Object value, @Nullable Argument<?> argument) {
        if (converterClass == null) {
            return value;
        }
        AttributeConverter<Object, Object> converter = attributeConverterRegistry.getConverter(converterClass);
        ConversionContext conversionContext = createTypeConversionContext(null, argument);
        return converter.convertToPersistedValue(value, conversionContext);
    }

    private Object convert(Object value, RuntimePersistentProperty<?> property) {
        AttributeConverter<Object, Object> converter = property.getConverter();
        if (converter != null) {
            return converter.convertToPersistedValue(value, createTypeConversionContext(property, property.getArgument()));
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

    private ConversionContext createTypeConversionContext(RuntimePersistentProperty<?> property, Argument<?> argument) {
        if (argument != null) {
            return ConversionContext.of(argument);
        }
        return ConversionContext.DEFAULT;
    }

    @Override
    public StoredQuery<E, R> getStoredQueryDelegate() {
        return storedQuery;
    }

    private final class AggregateData extends CollationSupported {
        private final List<Bson> pipeline;
        private final boolean pipelineNeedsProcessing;
        @Nullable
        private final MongoAggregationOptions options;
        private final int pipelineParameterIndex;
        private final int optionsParameterIndex;

        private AggregateData(List<Bson> pipeline) {
            this(pipeline, null, null);
        }

        private AggregateData(String pipelineParameter, String optionsParameter) {
            this(null, pipelineParameter, optionsParameter);
        }

        private AggregateData(List<Bson> pipeline, String pipelineParameter, String optionsParameter) {
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
            return new MongoAggregation(pipeline, options);
        }

        private List<Bson> getPipeline(InvocationContext<?, ?> invocationContext) {
            if (pipelineParameterIndex != -1) {
                return getParameterAtIndex(invocationContext, pipelineParameterIndex);
            }
            return pipelineNeedsProcessing ? replaceQueryParametersInList(this.pipeline, invocationContext, null) : this.pipeline;
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
        private final Bson update;
        private final boolean updateNeedsProcessing;
        private final Bson filter;
        private final boolean filterNeedsProcessing;
        @Nullable
        private final UpdateOptions options;
        private final int filterParameterIndex;
        private final int updateParameterIndex;
        private final int optionsParameterIndex;

        private UpdateData(Bson update, Bson filter, String filterParameter, String updateParameter, String optionsParameter) {
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
        }

        public MongoUpdate getUpdateMany(InvocationContext<?, ?> invocationContext) {
            return new MongoUpdate(
                    getUpdate(invocationContext, null),
                    getFilter(invocationContext, null),
                    getOptions(invocationContext));
        }

        public MongoUpdate getUpdateOne(E entity) {
            if (updateData == null) {
                throw new IllegalStateException("Expected update query!");
            }
            Bson update = getUpdate(null, entity);
            UpdateOptions options = getOptions(null);
            return new MongoUpdate(update, getFilter(null, entity), options);
        }

        private Bson getUpdate(InvocationContext<?, ?> invocationContext, E entity) {
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
        private UpdateOptions getOptions(InvocationContext<?, ?> invocationContext) {
            UpdateOptions options = this.options;
            if (optionsParameterIndex != -1) {
                UpdateOptions paramOptions = getParameterAtIndex(invocationContext, optionsParameterIndex);
                if (paramOptions != null) {
                    if (options == null) {
                        options = paramOptions;
                    } else {
                        options = copy(this.options);
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

        private Bson getFilter(@Nullable InvocationContext<?, ?> invocationContext, E entity) {
            if (filterParameterIndex != -1) {
                return getParameterAtIndex(invocationContext, filterParameterIndex);
            }
            return filterNeedsProcessing ? replaceQueryParameters(filter, invocationContext, entity) : filter;
        }
    }

    private final class FindData extends CollationSupported {
        private final Bson filter;
        private final boolean filterNeedsProcessing;
        private final Bson sort;
        private final boolean sortNeedsProcessing;
        private final Bson projection;
        private final boolean projectionNeedsProcessing;
        @Nullable
        private final MongoFindOptions options;
        private final int filterParameterIndex;
        private final int optionsParameterIndex;

        private FindData(Bson filter) {
            this(filter, null, null);
        }

        private FindData(String filterParameter, String optionsParameter) {
            this(null, filterParameter, optionsParameter);
        }

        private FindData(Bson filter, String filterParameter, String optionsParameter) {
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

        public MongoFind getFind(InvocationContext<?, ?> invocationContext) {
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

        private Bson getFilter(@Nullable InvocationContext<?, ?> invocationContext, E entity) {
            if (filterParameterIndex != -1) {
                return getParameterAtIndex(invocationContext, filterParameterIndex);
            }
            if (filter == null) {
                return null;
            }
            return filterNeedsProcessing ? replaceQueryParameters(filter, invocationContext, entity) : filter;
        }

        private Bson getSort(@Nullable InvocationContext<?, ?> invocationContext, @Nullable E entity) {
            if (sort == null) {
                return null;
            }
            return sortNeedsProcessing ? replaceQueryParameters(sort, invocationContext, entity) : sort;
        }

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

        private DeleteData(Bson filter, String filterParameter, String optionsParameter) {
            this.filter = filter;
            this.filterNeedsProcessing = needsProcessing(filter);
            this.filterParameterIndex = getParameterIndexByName(filterParameter);
            this.optionsParameterIndex = getParameterIndexByName(optionsParameter);
            options = MongoOptionsUtils.buildDeleteOptions(storedQuery.getAnnotationMetadata(), false).orElse(null);
        }

        public MongoDelete getDeleteMany(InvocationContext<?, ?> invocationContext) {
            DeleteOptions options = getOptions(invocationContext);
            return new MongoDelete(getFilter(invocationContext, null), options);
        }

        public MongoDelete getDeleteOne(E entity) {
            DeleteOptions options = getOptions(null);
            return new MongoDelete(getFilter(null, entity), options);
        }

        @NonNull
        private DeleteOptions getOptions(InvocationContext<?, ?> invocationContext) {
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

        private Bson getFilter(@Nullable InvocationContext<?, ?> invocationContext, E entity) {
            if (filterParameterIndex != -1) {
                return getParameterAtIndex(invocationContext, filterParameterIndex);
            }
            return filterNeedsProcessing ? replaceQueryParameters(filter, invocationContext, entity) : filter;
        }
    }

    private abstract class CollationSupported {
        private final Bson collationAsBson;
        private final boolean collationNeedsProcessing;
        private final Collation collation;

        protected CollationSupported() {
            collationAsBson = storedQuery.getAnnotationMetadata().stringValue(MongoCollation.class).map(BsonDocument::parse).orElse(null);
            collationNeedsProcessing = needsProcessing(collationAsBson);
            collation = collationAsBson == null || collationNeedsProcessing ? null : MongoOptionsUtils.bsonDocumentAsCollation(collationAsBson.toBsonDocument());
        }

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
