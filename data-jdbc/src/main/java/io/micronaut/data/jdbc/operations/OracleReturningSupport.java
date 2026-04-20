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
package io.micronaut.data.jdbc.operations;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.jdbc.mapper.CallableStatementTupleMapper;
import io.micronaut.data.jdbc.mapper.ColumnIndexCallableResultReader;
import io.micronaut.data.jdbc.mapper.ColumnNameByIndexCallableResultReader;
import io.micronaut.data.jdbc.mapper.ColumnNameExistenceAwareCallableResultReader;
import io.micronaut.data.jdbc.mapper.JdbcQueryStatement;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.runtime.QueryOutParameterBinding;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.mapper.ResultReader;
import io.micronaut.data.runtime.mapper.sql.SqlJsonColumnReader;
import io.micronaut.data.runtime.mapper.sql.SqlResultEntityTypeMapper;
import io.micronaut.data.runtime.mapper.sql.SqlTypeMapper;
import io.micronaut.data.runtime.operations.internal.sql.OracleReturningMetadata;
import io.micronaut.data.runtime.operations.internal.sql.SqlPreparedQuery;
import io.micronaut.data.runtime.operations.internal.sql.SqlStoredQuery;
import io.micronaut.json.JsonMapper;
import jakarta.persistence.Tuple;
import org.jspecify.annotations.Nullable;

import java.sql.CallableStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Static JDBC support for Oracle SQL {@code RETURNING} callable statement handling.
 *
 * @author Radovan Radic
 * @since 5.0
 */
@Internal
final class OracleReturningSupport {

    private OracleReturningSupport() {
    }

    static OutParameterContext registerOutParameters(CallableStatement cs,
                                                     SqlStoredQuery<?, ?> query,
                                                     int inCount,
                                                     Dialect dialect,
                                                     Consumer<String> debugLogger) throws SQLException {
        List<QueryOutParameterBinding> outParams = query.getOutParameterBindings();
        if (CollectionUtils.isEmpty(outParams)) {
            throw new DataAccessException("Missing OUT parameter metadata for Oracle RETURNING. SqlQueryBuilder must attach QueryOutParameterBinding list.");
        }
        int pos = inCount;
        List<String> columnNames = new ArrayList<>(outParams.size());
        List<Integer> columnIndexes = new ArrayList<>(outParams.size());
        for (QueryOutParameterBinding outParam : outParams) {
            DataType dataType = dialect.getDataType(outParam.dataType());
            int sqlType = JdbcQueryStatement.findSqlType(dataType, dialect);
            if (sqlType == -1) {
                sqlType = Types.VARCHAR;
                debugLogger.accept("Binding Oracle out parameter of data type: " + dataType + " as sql type: " + sqlType);
            }
            int columnIndex = ++pos;
            cs.registerOutParameter(columnIndex, sqlType);
            columnNames.add(outParam.name());
            columnIndexes.add(columnIndex);
        }
        return new OutParameterContext(inCount, OracleReturningMetadata.create(columnNames, columnIndexes));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static <E, R> SqlTypeMapper<CallableStatement, R> createMapper(SqlStoredQuery<E, R> query,
                                                                   OutParameterContext outCtx,
                                                                   ColumnIndexCallableResultReader columnIndexCallableResultReader,
                                                                   DataConversionService conversionService,
                                                                   @Nullable JsonMapper jsonMapper,
                                                                   Function<Class<?>, RuntimePersistentEntity<?>> entityResolver,
                                                                   DtoEntityResolver dtoEntityResolver,
                                                                   PostLoadCallback postLoadCallback) {
        ResultReader<CallableStatement, String> resultReader = new ColumnNameByIndexCallableResultReader(
            columnIndexCallableResultReader,
            outCtx.metadata().columnIndexesByName()
        );
        if (query.getResultType().equals(Tuple.class)) {
            return (SqlTypeMapper<CallableStatement, R>) new CallableStatementTupleMapper(
                conversionService,
                outCtx.metadata().canonicalColumnIndexesByName()
            );
        }
        if (query.getResultType().equals(Object[].class)) {
            SqlTypeMapper<CallableStatement, Tuple> tupleMapper = new CallableStatementTupleMapper(
                conversionService,
                outCtx.metadata().canonicalColumnIndexesByName()
            );
            return new SqlTypeMapper<>() {
                @Override
                public boolean hasNext(CallableStatement resultSet) {
                    throw new IllegalStateException("Not supported!");
                }

                @Override
                public @Nullable R map(CallableStatement object, Class<R> type) throws DataAccessException {
                    Tuple tuple = tupleMapper.map(object, Tuple.class);
                    return tuple == null ? null : (R) tuple.toArray();
                }

                @Override
                public @Nullable Object read(CallableStatement object, String name) {
                    return tupleMapper.read(object, name);
                }
            };
        }
        RuntimePersistentEntity<E> persistentEntity = query.getPersistentEntity();
        if (query.getResultDataType() == DataType.ENTITY) {
            RuntimePersistentEntity<R> resultPersistentEntity = (RuntimePersistentEntity<R>) entityResolver.apply(query.getResultType());
            return new SqlResultEntityTypeMapper<CallableStatement, R>(
                resultPersistentEntity,
                resultReader,
                query.getJoinPaths(),
                jsonMapper != null ? () -> jsonMapper : null,
                (loadedEntity, entity) -> (R) postLoadCallback.apply(loadedEntity, entity, query.getAnnotationMetadata()),
                conversionService
            );
        }
        if (dtoEntityResolver.isDtoProjection(query)) {
            ResultReader<CallableStatement, String> dtoResultReader = new ColumnNameExistenceAwareCallableResultReader(
                columnIndexCallableResultReader,
                outCtx.metadata().columnIndexesByName()
            );
            RuntimePersistentEntity<R> resultPersistentEntity = (RuntimePersistentEntity<R>) entityResolver.apply(query.getResultType());
            RuntimePersistentEntity<R> dtoPersistentEntity = (RuntimePersistentEntity<R>) dtoEntityResolver.resolve(
                query.getAnnotationMetadata(),
                persistentEntity,
                resultPersistentEntity
            );
            return new SqlResultEntityTypeMapper<CallableStatement, R>(
                dtoPersistentEntity,
                dtoResultReader,
                query.getJoinPaths(),
                null,
                null,
                conversionService
            );
        }
        return new SqlTypeMapper<>() {
            @Override
            public boolean hasNext(CallableStatement resultSet) {
                throw new IllegalStateException("Not supported!");
            }

            @Override
            public @Nullable R map(CallableStatement object, Class<R> type) throws DataAccessException {
                try {
                    return readScalarResult(object, (SqlPreparedQuery<?, R>) query, outCtx.firstOutIndex(), columnIndexCallableResultReader, conversionService);
                } catch (SQLException e) {
                    throw new DataAccessException("Error reading Oracle SQL RETURNING value: " + e.getMessage(), e);
                }
            }

            @Override
            public @Nullable Object read(CallableStatement object, String name) {
                throw new IllegalStateException("Not supported!");
            }
        };
    }

    static SqlResultEntityTypeMapper<CallableStatement, ?> createEntityMapper(RuntimePersistentEntity<?> persistentEntity,
                                                                               OutParameterContext outCtx,
                                                                               ColumnIndexCallableResultReader columnIndexCallableResultReader,
                                                                               @Nullable JsonMapper jsonMapper,
                                                                               DataConversionService conversionService) {
        ColumnNameByIndexCallableResultReader resultReader = new ColumnNameByIndexCallableResultReader(
            columnIndexCallableResultReader,
            outCtx.metadata().canonicalColumnIndexesByName()
        );
        SqlJsonColumnReader<CallableStatement> reader = jsonMapper != null ? () -> jsonMapper : null;
        return new SqlResultEntityTypeMapper<CallableStatement, Object>(
            (RuntimePersistentEntity<Object>) persistentEntity,
            resultReader,
            Set.of(),
            reader,
            conversionService
        );
    }

    private static @Nullable <R> R readScalarResult(CallableStatement cs,
                                                    SqlPreparedQuery<?, R> preparedQuery,
                                                    int columnIndex,
                                                    ColumnIndexCallableResultReader columnIndexCallableResultReader,
                                                    DataConversionService conversionService) throws SQLException {
        Object value = columnIndexCallableResultReader.readDynamic(
            cs,
            columnIndex,
            preparedQuery.getResultDataType()
        );
        if (value == null) {
            return null;
        }
        Class<R> resultType = preparedQuery.getResultType();
        if (resultType.isInstance(value)) {
            return resultType.cast(value);
        }
        return conversionService.convert(value, resultType)
            .orElseThrow(() -> new DataAccessException(
                "Error converting Oracle SQL RETURNING value of type " + value.getClass().getName() +
                    " to result type " + resultType.getName()
            ));
    }

    @FunctionalInterface
    interface DtoEntityResolver {
        RuntimePersistentEntity<?> resolve(AnnotationMetadata annotationMetadata,
                                           RuntimePersistentEntity<?> persistentEntity,
                                           RuntimePersistentEntity<?> resultPersistentEntity);

        default boolean isDtoProjection(SqlStoredQuery<?, ?> query) {
            return false;
        }
    }

    @FunctionalInterface
    interface PostLoadCallback {
        Object apply(RuntimePersistentEntity<?> loadedEntity, Object entity, AnnotationMetadata annotationMetadata);
    }

    record OutParameterContext(int inCount, OracleReturningMetadata metadata) {
        int firstOutIndex() {
            return inCount + 1;
        }
    }
}
