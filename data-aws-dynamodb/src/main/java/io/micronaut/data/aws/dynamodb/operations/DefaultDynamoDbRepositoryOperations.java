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
package io.micronaut.data.aws.dynamodb.operations;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ExecuteStatementRequest;
import com.amazonaws.services.dynamodbv2.model.ExecuteStatementResult;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.aws.dynamodb.mapper.DynamoDbResultEntityMapper;
import io.micronaut.data.aws.dynamodb.mapper.DynamoDbResultReader;
import io.micronaut.data.document.model.query.builder.DynamoDbSqlQueryBuilder;
import io.micronaut.data.exceptions.NonUniqueResultException;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.runtime.AttributeConverterRegistry;
import io.micronaut.data.model.runtime.DeleteBatchOperation;
import io.micronaut.data.model.runtime.DeleteOperation;
import io.micronaut.data.model.runtime.InsertOperation;
import io.micronaut.data.model.runtime.PagedQuery;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.model.runtime.UpdateOperation;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.date.DateTimeProvider;
import io.micronaut.data.runtime.mapper.TypeMapper;
import io.micronaut.data.runtime.mapper.sql.SqlDTOMapper;
import io.micronaut.data.runtime.mapper.sql.SqlResultEntityTypeMapper;
import io.micronaut.data.runtime.operations.internal.AbstractRepositoryOperations;
import io.micronaut.data.runtime.operations.internal.sql.DefaultSqlPreparedQuery;
import io.micronaut.data.runtime.operations.internal.sql.DefaultSqlStoredQuery;
import io.micronaut.data.runtime.operations.internal.sql.SqlPreparedQuery;
import io.micronaut.data.runtime.query.MethodContextAwareStoredQueryDecorator;
import io.micronaut.data.runtime.query.PreparedQueryDecorator;
import io.micronaut.http.codec.MediaTypeCodec;
import jakarta.inject.Singleton;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Default AWS DynamoDB repository operations implementation.
 *
 * @author radovanradic
 * @since 4.0.0
 */
@Singleton
@Requires(classes = AmazonDynamoDB.class)
@Internal
public class DefaultDynamoDbRepositoryOperations extends AbstractRepositoryOperations implements
    PreparedQueryDecorator,
    MethodContextAwareStoredQueryDecorator,
    DynamoDbRepositoryOperations {

    private final AmazonDynamoDB amazonDynamoDB;

    /**
     * Default constructor.
     *
     * @param amazonDynamoDB the Amazon DynamoDB client
     * @param codecs                     The media type codecs
     * @param dateTimeProvider           The date time provider
     * @param runtimeEntityRegistry      The entity registry
     * @param conversionService          The conversion service
     * @param attributeConverterRegistry The attribute converter registry
     */
    public DefaultDynamoDbRepositoryOperations(@NonNull AmazonDynamoDB amazonDynamoDB,
                                               List<MediaTypeCodec> codecs,
                                               DateTimeProvider<Object> dateTimeProvider,
                                               RuntimeEntityRegistry runtimeEntityRegistry,
                                               DataConversionService conversionService,
                                               AttributeConverterRegistry attributeConverterRegistry) {
        super(codecs, dateTimeProvider, runtimeEntityRegistry, conversionService, attributeConverterRegistry);
        this.amazonDynamoDB = amazonDynamoDB;
    }

    @Override
    public <T> T findOne(Class<T> type, Serializable id) {
        // TODO: When is this used?
        return null;
    }

    @Override
    public <T, R> R findOne(PreparedQuery<T, R> preparedQuery) {

        SqlPreparedQuery<T, R> sqlPreparedQuery = getSqlPreparedQuery(preparedQuery);
        sqlPreparedQuery.prepare(null);

        ExecuteStatementRequest executeStatementRequest = new ExecuteStatementRequest();
        executeStatementRequest.setStatement(preparedQuery.getQuery());

        boolean isRawQuery = isRawQuery(preparedQuery);
        List<AttributeValue> parameterValues = new ArrayList<>();
        RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(preparedQuery.getRootEntity());
        DynamoDbBinder dynamoDbBinder = new DynamoDbBinder(runtimeEntityRegistry, attributeConverterRegistry, parameterValues, isRawQuery,
            false, persistentEntity, Collections.emptyList());
        sqlPreparedQuery.bindParameters(dynamoDbBinder);
        executeStatementRequest.setParameters(parameterValues);

        ExecuteStatementResult result = amazonDynamoDB.executeStatement(executeStatementRequest);
        if (CollectionUtils.isEmpty(result.getItems())) {
            return null;
        }
        if (result.getItems().size() > 1) {
            throw new NonUniqueResultException();
        }
        Class<R> resultType = preparedQuery.getResultType();
        DynamoDbResultReader resultReader = new DynamoDbResultReader();
        if (preparedQuery.getResultDataType() == DataType.ENTITY) {
            RuntimePersistentEntity<R> resultPersistentEntity = getEntity(resultType);
            //TypeMapper<List<Map<String, AttributeValue>>, R> mapper = new SqlResultEntityTypeMapper<>(
            //    null, resultPersistentEntity, resultReader, jsonCodec, conversionService);
            TypeMapper<List<Map<String, AttributeValue>>, R> mapper = new DynamoDbResultEntityMapper<>(
                            resultPersistentEntity, resultReader, jsonCodec, conversionService);
            return mapper.map(result.getItems(), preparedQuery.getResultType());
        } else {
            if (preparedQuery.isDtoProjection()) {
                // TODO: Implement DynamoDB DTO mapper as well
                TypeMapper<List<Map<String, AttributeValue>>, R> introspectedDataMapper = new SqlDTOMapper<>(
                    persistentEntity,
                    isRawQuery ? getEntity(preparedQuery.getResultType()) : persistentEntity,
                    resultReader,
                    jsonCodec,
                    conversionService
                );
                return introspectedDataMapper.map(result.getItems(), resultType);
            } else {
                // TODO: Single resulting field
                int x = 1;
            }
        }
        return null;
    }

    @Override
    public <T> boolean exists(PreparedQuery<T, Boolean> preparedQuery) {
        return false;
    }

    @Override
    public <T> Iterable<T> findAll(PagedQuery<T> query) {
        return null;
    }

    @Override
    public <T> long count(PagedQuery<T> pagedQuery) {
        return 0;
    }

    @Override
    public <T, R> Iterable<R> findAll(PreparedQuery<T, R> preparedQuery) {
        return null;
    }

    @Override
    public <T, R> Stream<R> findStream(PreparedQuery<T, R> preparedQuery) {
        return null;
    }

    @Override
    public <T> Stream<T> findStream(PagedQuery<T> query) {
        return null;
    }

    @Override
    public <R> Page<R> findPage(PagedQuery<R> query) {
        return null;
    }

    @Override
    public <T> T persist(InsertOperation<T> operation) {
        return null;
    }

    @Override
    public <T> T update(UpdateOperation<T> operation) {
        return null;
    }

    @Override
    public Optional<Number> executeUpdate(PreparedQuery<?, Number> preparedQuery) {
        return Optional.empty();
    }

    @Override
    public <T> int delete(DeleteOperation<T> operation) {
        return 0;
    }

    @Override
    public <T> Optional<Number> deleteAll(DeleteBatchOperation<T> operation) {
        return Optional.empty();
    }

    @Override
    public <E, R> StoredQuery<E, R> decorate(MethodInvocationContext<?, ?> context, StoredQuery<E, R> storedQuery) {
        RuntimePersistentEntity<E> runtimePersistentEntity = runtimeEntityRegistry.getEntity(storedQuery.getRootEntity());
        return new DefaultSqlStoredQuery<>(storedQuery, runtimePersistentEntity , new DynamoDbSqlQueryBuilder(context.getAnnotationMetadata()));
    }

    @Override
    public <E, R> PreparedQuery<E, R> decorate(PreparedQuery<E, R> preparedQuery) {
        return new DefaultSqlPreparedQuery<E, R>(preparedQuery);
    }

    /**
     * Gets an indicator telling whether {@link PreparedQuery} is raw query.
     *
     * @param preparedQuery the prepared query
     * @return true if prepared query is created from raw query
     */
    private boolean isRawQuery(@NonNull PreparedQuery<?, ?> preparedQuery) {
        return preparedQuery.getAnnotationMetadata().stringValue(Query.class, DataMethod.META_MEMBER_RAW_QUERY).isPresent();
    }

    private <E, R> SqlPreparedQuery<E, R> getSqlPreparedQuery(PreparedQuery<E, R> preparedQuery) {
        if (preparedQuery instanceof SqlPreparedQuery) {
            return (SqlPreparedQuery<E, R>) preparedQuery;
        }
        throw new IllegalStateException("Expected for prepared query to be of type: SqlPreparedQuery got: " + preparedQuery.getClass().getName());
    }
}
