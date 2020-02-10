package io.micronaut.data.cql.operations;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BatchStatement;
import com.datastax.oss.driver.api.core.cql.BatchStatementBuilder;
import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder;
import com.datastax.oss.driver.api.core.cql.DefaultBatchType;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.BeanWrapper;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.DateUpdated;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.runtime.BatchOperation;
import io.micronaut.data.model.runtime.InsertOperation;
import io.micronaut.data.model.runtime.PagedQuery;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.RuntimeAssociation;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.model.runtime.UpdateOperation;
import io.micronaut.data.runtime.date.DateTimeProvider;
import io.micronaut.data.runtime.mapper.DTOMapper;
import io.micronaut.data.runtime.mapper.ResultConsumer;
import io.micronaut.data.runtime.mapper.ResultReader;
import io.micronaut.data.runtime.mapper.TypeMapper;
import io.micronaut.data.runtime.mapper.sql.SqlDTOMapper;
import io.micronaut.data.runtime.mapper.sql.SqlResultEntityTypeMapper;
import io.micronaut.data.runtime.mapper.sql.SqlTypeMapper;
import io.micronaut.data.runtime.operations.ExecutorAsyncOperations;
import io.micronaut.http.codec.MediaTypeCodec;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@EachBean(CqlSession.class)
public class DefaultCqlRepositoryOperations extends AbstractCqlRepositoryOperations<ResultWrapper, BoundStatementBuilder> implements CqlRepositoryOperations {

    private static final Object IGNORED_PARAMETER = new Object();
    private ExecutorAsyncOperations asyncOperations;
    private ExecutorService executorService;
    private CqlSession cqlSession;

    protected DefaultCqlRepositoryOperations(
        List<MediaTypeCodec> codecs,
        BeanContext beanContext,
        CqlSession session,
        DateTimeProvider dateTimeProvider
    ){
        super(new ColumnNameResultSetReader(),new ColumnIndexResultSetReader(),new CqlQueryStatement(), codecs, dateTimeProvider);
        this.cqlSession = session;
        this.executorService = executorService;
//        Collection<BeanDefinition<GenericRepository>> beanDefinitions = beanContext.getBeanDefinitions(GenericRepository.class, Qualifiers.byStereotype(Repository.class));
//        for (BeanDefinition<GenericRepository> beanDefinition : beanDefinitions) {
//            String targetDs = beanDefinition.stringValue(Repository.class).orElse("default");
//            if (targetDs.equalsIgnoreCase(dataSourceName)) {
//                Dialect dialect = beanDefinition.enumValue(JdbcRepository.class, "dialect", Dialect.class).orElseGet(() -> beanDefinition.enumValue(JdbcRepository.class, "dialectName", Dialect.class).orElse(Dialect.ANSI));
//                dialects.put(beanDefinition.getBeanType(), dialect);
//                QueryBuilder qb = queryBuilders.get(dialect);
//                if (qb == null) {
//                    queryBuilders.put(dialect, new SqlQueryBuilder(dialect));
//                }
//            }
//        }
    }

    @NonNull
    private ExecutorService newLocalThreadPool() {
        this.executorService = Executors.newCachedThreadPool();
        return executorService;
    }
//
//    @NonNull
//    @Override
//    public ExecutorAsyncOperations async() {
//        ExecutorAsyncOperations asyncOperations = this.asyncOperations;
//        if (asyncOperations == null) {
//            synchronized (this) { // double check
//                asyncOperations = this.asyncOperations;
//                if (asyncOperations == null) {
//                    asyncOperations = new ExecutorAsyncOperations(
//                        this,
//                        executorService != null ? executorService : newLocalThreadPool()
//                    );
//                    this.asyncOperations = asyncOperations;
//                }
//            }
//        }
//        return asyncOperations;
//    }
//
//    @NonNull
//    @Override
//    public ReactiveRepositoryOperations reactive() {
//        return new ExecutorReactiveOperations(async());
//    }

    @Nullable
    @Override
    public <T, R> R findOne(@NonNull PreparedQuery<T, R> preparedQuery) {
        BoundStatementBuilder builder = prepareStatement(this.cqlSession, preparedQuery, false, true);
        ResultWrapper rs =  ResultWrapper.of(this.cqlSession.execute(builder.build()));
        if(rs.next()){
            Class<T> rootEntity = preparedQuery.getRootEntity();
            Class<R> resultType = preparedQuery.getResultType();
            if(resultType == rootEntity){
                RuntimePersistentEntity<R> persistentEntity = getEntity((Class<R>) rootEntity);
                TypeMapper<ResultWrapper,R> mapper = new SqlResultEntityTypeMapper<>(
                    persistentEntity,
                    columnNameResultSetReader,
                    preparedQuery.getJoinFetchPaths(),
                    jsonCodec
                );
                R result = mapper.map(rs,resultType);
                if(preparedQuery.hasResultConsumer()){
                }
                return result;
            } else  {
                if(preparedQuery.isDtoProjection()) {
                    RuntimePersistentEntity<T> persistentEntity = getEntity(preparedQuery.getRootEntity());
                    TypeMapper<ResultWrapper,R> introspectedDataMapper = new DTOMapper<>(
                        persistentEntity,
                        columnNameResultSetReader
                    );
                    return introspectedDataMapper.map(rs,resultType);
                } else {
                    Object v = columnIndexResultSetReader.readDynamic(rs,1 ,preparedQuery.getResultDataType());
                    if(resultType.isInstance(v)){
                        return  (R) v;
                    } else {
                        return columnIndexResultSetReader.convertRequired(v,resultType);
                    }
                }
            }
        }
        return null;

//        for(Row row:  wrapper) {
//            if(resultType == rootEntity) {
//
//            }
//            else {
//                if(preparedQuery.isDtoProjection()) {
//
//                    TypeMapper<ResultWrapper, R> introspectionDataMapper = new DTOMapper<>(persistentEntity,
//                        columnNameResultSetReader);
//                    return introspectionDataMapper.map(wrapper, resultType);
//                } else  {
//
//                }
//
//            }
//
//        }


//        BoundStatementBuilder boundStatementBuilder = this.cqlSession


//        return transactionOperations.executeRead(status -> {
//            Connection connection = status.getConnection();
//            try (PreparedStatement ps = prepareStatement(connection, preparedQuery, false, true)) {
//                try (java.sql.ResultSet rs = ps.executeQuery()) {
//                    if (rs.next()) {
//                        Class<T> rootEntity = preparedQuery.getRootEntity();
//                        Class<R> resultType = preparedQuery.getResultType();
//                        if (resultType == rootEntity) {
//                            @SuppressWarnings("unchecked")
//                            RuntimePersistentEntity<R> persistentEntity = getEntity((Class<R>) rootEntity);
//                            TypeMapper<java.sql.ResultSet, R> mapper = new SqlResultEntityTypeMapper<>(
//                                persistentEntity,
//                                columnNameResultSetReader,
//                                preparedQuery.getJoinFetchPaths(),
//                                jsonCodec
//                            );
//                            R result = mapper.map(rs, resultType);
//                            if (preparedQuery.hasResultConsumer()) {
//                                preparedQuery.getParameterInRole(SqlResultConsumer.ROLE, SqlResultConsumer.class)
//                                    .ifPresent(consumer -> consumer.accept(result, newMappingContext(rs)));
//                            }
//                            return result;
//                        } else {
//                            if (preparedQuery.isDtoProjection()) {
//                                RuntimePersistentEntity<T> persistentEntity = getEntity(preparedQuery.getRootEntity());
//                                TypeMapper<java.sql.ResultSet, R> introspectedDataMapper = new DTOMapper<>(
//                                    persistentEntity,
//                                    columnNameResultSetReader
//                                );
//
//                                return introspectedDataMapper.map(rs, resultType);
//                            } else {
//                                Object v = columnIndexResultSetReader.readDynamic(rs, 1, preparedQuery.getResultDataType());
//                                if (resultType.isInstance(v)) {
//                                    return (R) v;
//                                } else {
//                                    return columnIndexResultSetReader.convertRequired(v, resultType);
//                                }
//                            }
//                        }
//                    }
//                }
//            } catch (SQLException e) {
//                throw new DataAccessException("Error executing SQL Query: " + e.getMessage(), e);
//            }
//            return null;
//        });
    }

    @NonNull
    private ResultConsumer.Context<ResultWrapper> newMappingContext(ResultWrapper rs) {
        return new ResultConsumer.Context<ResultWrapper>() {
            @Override
            public ResultWrapper getResultSet() {
                return rs;
            }

            @Override
            public ResultReader<ResultWrapper, String> getResultReader() {
                return columnNameResultSetReader;
            }

            @NonNull
            @Override
            public <E> E readEntity(String prefix, Class<E> type) throws DataAccessException {
                RuntimePersistentEntity<E> entity = getEntity(type);
                TypeMapper<ResultWrapper, E> mapper = new SqlResultEntityTypeMapper<>(
                    prefix,
                    entity,
                    columnNameResultSetReader,
                    jsonCodec
                );
                return mapper.map(rs, type);
            }

            @NonNull
            @Override
            public <E, D> D readDTO(@NonNull String prefix, @NonNull Class<E> rootEntity, @NonNull Class<D> dtoType) throws DataAccessException {
                RuntimePersistentEntity<E> entity = getEntity(rootEntity);
                TypeMapper<ResultWrapper, D> introspectedDataMapper = new DTOMapper<>(
                    entity,
                    columnNameResultSetReader
                );
                return introspectedDataMapper.map(rs, dtoType);
            }
        };
    }

    @Override
    public <T, R> boolean exists(@NonNull PreparedQuery<T, R> preparedQuery) {

        BoundStatementBuilder builder = prepareStatement(this.cqlSession, preparedQuery, false, true);
        ResultWrapper rs =  ResultWrapper.of(this.cqlSession.execute(builder.build()));
        return rs.next();

//        //noinspection ConstantConditions
//        return transactionOperations.executeRead(status -> {
//            try {
//                Connection connection = status.getConnection();
//                PreparedStatement ps = prepareStatement(connection, preparedQuery, false, true);
//                java.sql.ResultSet rs = ps.executeQuery();
//                return rs.next();
//            } catch (SQLException e) {
//                throw new DataAccessException("Error executing SQL query: " + e.getMessage(), e);
//            }
//        });
    }

    @NonNull
    @Override
    public <T, R> Stream<R> findStream(@NonNull PreparedQuery<T, R> preparedQuery) {
        return findStream(preparedQuery, this.cqlSession);
//
//        //noinspection ConstantConditions
//        return transactionOperations.executeRead(status -> {
//            Connection connection = status.getConnection();
//            return findStream(preparedQuery, connection);
//        });
    }

    private <T, R> Stream<R> findStream(@NonNull PreparedQuery<T, R> preparedQuery, CqlSession session) {
        Class<T> rootEntity = preparedQuery.getRootEntity();
        Class<R> resultType = preparedQuery.getResultType();

        BoundStatementBuilder builder = prepareStatement(session, preparedQuery, false, false);

        ResultWrapper rs = ResultWrapper.of(this.cqlSession.execute(builder.build()));
        boolean dtoProjection = preparedQuery.isDtoProjection();
        boolean isRootResult = resultType == rootEntity;
        Spliterator<R> spliterator;
        AtomicBoolean finished = new AtomicBoolean();

        if (isRootResult || dtoProjection) {
            SqlTypeMapper<ResultWrapper, R> mapper;
            if(dtoProjection) {
                mapper = new SqlDTOMapper<>(
                    getEntity(rootEntity),
                    columnNameResultSetReader
                );
            } else  {
                mapper = new SqlResultEntityTypeMapper<>(
                    getEntity(resultType),
                    columnNameResultSetReader,
                    preparedQuery.getJoinFetchPaths(),
                    jsonCodec
                );
            }

            spliterator = new Spliterators.AbstractSpliterator<R>(Long.MAX_VALUE,
                Spliterator.ORDERED | Spliterator.IMMUTABLE) {
                @Override
                public boolean tryAdvance(Consumer<? super R> action) {
                    if (finished.get()) {
                        return false;
                    }
                    boolean hasNext = mapper.hasNext(rs);
                    if (hasNext) {
                        R o = mapper.map(rs, resultType);
                        action.accept(o);
                    }
                    return hasNext;
                }
            };
        }
        else {
            //TODO: implemented
            return null;
        }
        return StreamSupport.stream(spliterator, false);

//        java.sql.ResultSet rs;
//        try {
//            rs = ps.executeQuery();
//        } catch (SQLException e) {
//            try {
//                ps.close();
//            } catch (SQLException e2) {
//                // ignore
//            }
//            throw new DataAccessException("SQL Error executing Query: " + e.getMessage(), e);
//        }
//        boolean dtoProjection = preparedQuery.isDtoProjection();
//        boolean isRootResult = resultType == rootEntity;
//        Spliterator<R> spliterator;
//        AtomicBoolean finished = new AtomicBoolean();
//        if (isRootResult || dtoProjection) {
//            SqlResultConsumer sqlMappingConsumer = preparedQuery.hasResultConsumer() ? preparedQuery.getParameterInRole(SqlResultConsumer.ROLE, SqlResultConsumer.class).orElse(null) : null;
//            SqlTypeMapper<java.sql.ResultSet, R> mapper;
//            if (dtoProjection) {
//                mapper = new SqlDTOMapper<>(
//                    getEntity(rootEntity),
//                    columnNameResultSetReader
//                );
//            } else {
//                mapper = new SqlResultEntityTypeMapper<>(
//                    getEntity(resultType),
//                    columnNameResultSetReader,
//                    preparedQuery.getJoinFetchPaths(),
//                    jsonCodec
//                );
//            }
//            spliterator = new Spliterators.AbstractSpliterator<R>(Long.MAX_VALUE,
//                Spliterator.ORDERED | Spliterator.IMMUTABLE) {
//                @Override
//                public boolean tryAdvance(Consumer<? super R> action) {
//                    if (finished.get()) {
//                        return false;
//                    }
//                    boolean hasNext = mapper.hasNext(rs);
//                    if (hasNext) {
//                        R o = mapper.map(rs, resultType);
//                        if (sqlMappingConsumer != null) {
//                            sqlMappingConsumer.accept(rs, o);
//                        }
//                        action.accept(o);
//                    } else {
//                        closeResultSet(ps, rs, finished);
//                    }
//                    return hasNext;
//                }
//            };
//        } else {
//            spliterator = new Spliterators.AbstractSpliterator<R>(Long.MAX_VALUE,
//                Spliterator.ORDERED | Spliterator.IMMUTABLE) {
//                @Override
//                public boolean tryAdvance(Consumer<? super R> action) {
//                    if (finished.get()) {
//                        return false;
//                    }
//                    try {
//                        boolean hasNext = rs.next();
//                        if (hasNext) {
//                            Object v = columnIndexResultSetReader.readDynamic(rs, 1, preparedQuery.getResultDataType());
//                            if (resultType.isInstance(v)) {
//                                //noinspection unchecked
//                                action.accept((R) v);
//                            } else {
//                                Object r = columnIndexResultSetReader.convertRequired(v, resultType);
//                                action.accept((R) r);
//                            }
//                        } else {
//                            closeResultSet(ps, rs, finished);
//                        }
//                        return hasNext;
//                    } catch (SQLException e) {
//                        throw new DataAccessException("Error retrieving next JDBC result: " + e.getMessage(), e);
//                    }
//                }
//            };
//        }
//
//        return StreamSupport.stream(spliterator, false).onClose(() -> {
//            closeResultSet(ps, rs, finished);
//        });
    }
//
//    private void closeResultSet(PreparedStatement ps, ResultWrapper rs, AtomicBoolean finished) {
//        if (finished.compareAndSet(false, true)) {
//            try {
//                rs.close();
//                ps.close();
//            } catch (SQLException e) {
//                throw new DataAccessException("Error closing JDBC result stream: " + e.getMessage(), e);
//            }
//        }
//    }

    @NonNull
    @Override
    public <T, R> Iterable<R> findAll(@NonNull PreparedQuery<T, R> preparedQuery) {
        return findStream(preparedQuery, this.cqlSession).collect(Collectors.toList());
//        return transactionOperations.executeRead(status -> {
//            Connection connection = status.getConnection();
//
//        });
    }

    @NonNull
    @Override
    public Optional<Number> executeUpdate(@NonNull PreparedQuery<?, Number> preparedQuery) {
        BoundStatementBuilder builder = prepareStatement(this.cqlSession,preparedQuery,true,false);
        this.cqlSession.execute(builder.build());
        return Optional.of(1);
//
//        //noinspection ConstantConditions
//        return transactionOperations.executeWrite(status -> {
//            try {
//                Connection connection = status.getConnection();
//                try (PreparedStatement ps = prepareStatement(connection, preparedQuery, true, false)) {
//                    return Optional.of(ps.executeUpdate());
//                }
//            } catch (SQLException e) {
//                throw new DataAccessException("Error executing SQL UPDATE: " + e.getMessage(), e);
//            }
//        });
    }

    @Override
    public <T> Optional<Number> deleteAll(@NonNull BatchOperation<T> operation) {
        throw new UnsupportedOperationException("The deleteAll method via batch is unsupported. Execute the SQL update directly");
    }

    @NonNull
    @Override
    public <T> T update(@NonNull UpdateOperation<T> operation) {
        final AnnotationMetadata annotationMetadata = operation.getAnnotationMetadata();
        final String[] params = annotationMetadata.stringValues(DataMethod.class, DataMethod.META_MEMBER_PARAMETER_BINDING_PATHS);
        final String query = annotationMetadata.stringValue(Query.class).orElse(null);
        final T entity = operation.getEntity();
        final Set persisted = new HashSet(10);
        final Class<?> repositoryType = operation.getRepositoryType();
        return updateOne(repositoryType, annotationMetadata, query, params, entity, persisted);
    }

    private <T> T updateOne(Class<?> repositoryType, AnnotationMetadata annotationMetadata, String query, String[] params, T entity, Set persisted) {
        Objects.requireNonNull(entity, "Passed entity cannot be null");
        if (StringUtils.isNotEmpty(query) && ArrayUtils.isNotEmpty(params)) {
            final RuntimePersistentEntity<T> persistentEntity = (RuntimePersistentEntity<T>) getEntity(entity.getClass());


            if (QUERY_LOG.isDebugEnabled()) {
                QUERY_LOG.debug("Executing SQL UPDATE: {}", query);
            }
            PreparedStatement stmt = this.cqlSession.prepare(query);
            BatchStatementBuilder batchBuilder = BatchStatement.builder(DefaultBatchType.LOGGED);

            BoundStatementBuilder builder = stmt.boundStatementBuilder();

            for (int i = 0; i < params.length; i++) {
                String propertyName = params[i];
                RuntimePersistentProperty<T> pp = persistentEntity.getPropertyByName(propertyName);
                int j = propertyName.indexOf('.');
                if (pp == null) {
                    if (j > -1) {
                        RuntimePersistentProperty embeddedProp = (RuntimePersistentProperty)
                            persistentEntity.getPropertyByPath(propertyName).orElse(null);
                        if (embeddedProp != null) {
                            // embedded case
                            pp = persistentEntity.getPropertyByName(propertyName.substring(0, j));
                            if (pp instanceof Association) {
                                Association assoc = (Association) pp;
                                if (assoc.getKind() == Relation.Kind.EMBEDDED) {
                                    Object embeddedInstance = pp.getProperty().get(entity);

                                    Object embeddedValue = embeddedInstance != null ? embeddedProp.getProperty().get(embeddedInstance) : null;
                                    int index = i + 1;
                                    preparedStatementWriter.setDynamic(
                                        builder,
                                        index,
                                        embeddedProp.getDataType(),
                                        embeddedValue
                                    );
                                }
                            }
                        } else {
                            throw new IllegalStateException("Cannot perform update for non-existent property: " + persistentEntity.getSimpleName() + "." + propertyName);
                        }
                    } else {
                        throw new IllegalStateException("Cannot perform update for non-existent property: " + persistentEntity.getSimpleName() + "." + propertyName);
                    }
                } else {
                    final Object newVale;
                    final BeanProperty<T, ?> beanProperty = pp.getProperty();

                }
            }

//            return transactionOperations.executeWrite(status -> {
//                try {
//                    Connection connection = status.getConnection();
//
//                    try (PreparedStatement ps = connection.prepareStatement(query)) {
//                        for (int i = 0; i < params.length; i++) {
//                            String propertyName = params[i];
//                            RuntimePersistentProperty<T> pp =
//                                persistentEntity.getPropertyByName(propertyName);
//                            if (pp == null) {
//                                int j = propertyName.indexOf('.');
//                                if (j > -1) {
//                                    RuntimePersistentProperty embeddedProp = (RuntimePersistentProperty)
//                                        persistentEntity.getPropertyByPath(propertyName).orElse(null);
//                                    if (embeddedProp != null) {
//
//                                        // embedded case
//                                        pp = persistentEntity.getPropertyByName(propertyName.substring(0, j));
//                                        if (pp instanceof Association) {
//                                            Association assoc = (Association) pp;
//                                            if (assoc.getKind() == Relation.Kind.EMBEDDED) {
//                                                Object embeddedInstance = pp.getProperty().get(entity);
//
//                                                Object embeddedValue = embeddedInstance != null ? embeddedProp.getProperty().get(embeddedInstance) : null;
//                                                int index = i + 1;
//                                                preparedStatementWriter.setDynamic(
//                                                    ps,
//                                                    index,
//                                                    embeddedProp.getDataType(),
//                                                    embeddedValue
//                                                );
//                                            }
//                                        }
//                                    } else {
//                                        throw new IllegalStateException("Cannot perform update for non-existent property: " + persistentEntity.getSimpleName() + "." + propertyName);
//                                    }
//                                } else {
//                                    throw new IllegalStateException("Cannot perform update for non-existent property: " + persistentEntity.getSimpleName() + "." + propertyName);
//                                }
//                            } else {
//
//                                final Object newValue;
//                                final BeanProperty<T, ?> beanProperty = pp.getProperty();
//                                if (beanProperty.hasAnnotation(DateUpdated.class)) {
//                                    newValue = dateTimeProvider.getNow();
//                                    beanProperty.convertAndSet(entity, newValue);
//                                } else {
//                                    newValue = beanProperty.get(entity);
//                                }
//                                final DataType dataType = pp.getDataType();
//                                if (dataType == DataType.ENTITY && newValue != null && pp instanceof Association) {
//                                    final RuntimePersistentProperty<Object> idReader = getIdReader(newValue);
//                                    final Association association = (Association) pp;
//                                    final BeanProperty<Object, ?> idReaderProperty = idReader.getProperty();
//                                    final Object id = idReaderProperty.get(newValue);
//                                    if (QUERY_LOG.isTraceEnabled()) {
//                                        QUERY_LOG.trace("Binding parameter at position {} to value {}", i + 1, id);
//                                    }
//                                    if (id != null) {
//
//                                        preparedStatementWriter.setDynamic(
//                                            ps,
//                                            i + 1,
//                                            idReader.getDataType(),
//                                            id
//                                        );
//                                        if (association.doesCascade(Relation.Cascade.PERSIST) && !persisted.contains(newValue)) {
//                                            final Relation.Kind kind = association.getKind();
//                                            final RuntimePersistentEntity associatedEntity = (RuntimePersistentEntity) association.getAssociatedEntity();
//                                            switch (kind) {
//                                                case ONE_TO_ONE:
//                                                case MANY_TO_ONE:
//                                                    persisted.add(newValue);
//                                                    final StoredInsert<Object> updateStatement = resolveEntityUpdate(
//                                                        annotationMetadata,
//                                                        repositoryType,
//                                                        associatedEntity.getIntrospection().getBeanType(),
//                                                        associatedEntity
//                                                    );
//                                                    updateOne(
//                                                        repositoryType,
//                                                        annotationMetadata,
//                                                        updateStatement.getSql(),
//                                                        updateStatement.getParameterBinding(),
//                                                        newValue,
//                                                        persisted
//                                                    );
//                                                    break;
//                                                case MANY_TO_MANY:
//                                                case ONE_TO_MANY:
//                                                    // TODO: handle cascading updates to collections?
//
//                                                case EMBEDDED:
//                                                default:
//                                                    // TODO: embedded type updates
//                                            }
//                                        }
//                                    } else {
//                                        if (association.doesCascade(Relation.Cascade.PERSIST) && !persisted.contains(newValue)) {
//                                            final RuntimePersistentEntity associatedEntity = (RuntimePersistentEntity) association.getAssociatedEntity();
//
//                                            StoredInsert associatedInsert = resolveEntityInsert(
//                                                annotationMetadata,
//                                                repositoryType,
//                                                associatedEntity.getIntrospection().getBeanType(),
//                                                associatedEntity
//                                            );
//                                            persistOne(
//                                                annotationMetadata,
//                                                repositoryType,
//                                                associatedInsert,
//                                                newValue,
//                                                persisted
//                                            );
//                                            final Object assignedId = idReaderProperty.get(newValue);
//                                            if (assignedId != null) {
//                                                preparedStatementWriter.setDynamic(
//                                                    ps,
//                                                    i + 1,
//                                                    idReader.getDataType(),
//                                                    assignedId
//                                                );
//                                            }
//                                        }
//                                    }
//                                } else {
//                                    if (QUERY_LOG.isTraceEnabled()) {
//                                        QUERY_LOG.trace("Binding parameter at position {} to value {}", i + 1, newValue);
//                                    }
//                                    preparedStatementWriter.setDynamic(
//                                        ps,
//                                        i + 1,
//                                        dataType,
//                                        newValue
//                                    );
//                                }
//                            }
//                        }
//                        ps.executeUpdate();
//                        return entity;
//                    }
//                } catch (SQLException e) {
//                    throw new DataAccessException("Error executing SQL UPDATE: " + e.getMessage(), e);
//                }
//            });
        }
        return entity;
    }

    @NonNull
    @Override
    public <T> T persist(@NonNull InsertOperation<T> operation) {
        @SuppressWarnings("unchecked") StoredInsert<T> insert = resolveInsert(operation);
        final Class<?> repositoryType = operation.getRepositoryType();
        final AnnotationMetadata annotationMetadata = operation.getAnnotationMetadata();
        T entity = operation.getEntity();
        return persistOne(annotationMetadata, repositoryType, insert, entity, new HashSet(5));
    }

    private <T> T persistOne(
        AnnotationMetadata annotationMetadata,
        Class<?> repositoryType,
        StoredInsert<T> insert,
        T entity,
        Set persisted) {

        boolean generateId = insert.isGenerateId();
        String insertSql = insert.getSql();
        BeanProperty<T, Object> identity = insert.getIdentityProperty();
        final boolean hasGeneratedID = generateId && identity != null;

        if (QUERY_LOG.isDebugEnabled()) {
            QUERY_LOG.debug("Executing SQL Insert: {}", insertSql);
        }

        PreparedStatement stmt = this.cqlSession.prepare(insertSql);
        BoundStatementBuilder builder = stmt.boundStatementBuilder();
        setInsertParameters(insert, entity, builder);
        persisted.add(entity);
        return entity;


//        //noinspection ConstantConditions
//        return transactionOperations.executeWrite((status) -> {
//            try {
//                Connection connection = status.getConnection();
//
//
//
//                PreparedStatement stmt;
//                if (hasGeneratedID && (insert.getDialect() == Dialect.ORACLE || insert.getDialect() == Dialect.SQL_SERVER)) {
//                    stmt = connection
//                        .prepareStatement(insertSql, new String[] { insert.getIdentity().getPersistedName() });
//                } else {
//                    stmt = connection
//                        .prepareStatement(insertSql, generateId ? java.sql.Statement.RETURN_GENERATED_KEYS : java.sql.Statement.NO_GENERATED_KEYS);
//                }
//
//                setInsertParameters(insert, entity, stmt);
//                stmt.executeUpdate();
//                persisted.add(entity);
//                if (hasGeneratedID) {
//                    java.sql.ResultSet generatedKeys = stmt.getGeneratedKeys();
//                    if (generatedKeys.next()) {
//                        long id = generatedKeys.getLong(1);
//                        if (identity.getType().isInstance(id)) {
//                            identity.set(entity, id);
//                        } else {
//                            identity.convertAndSet(entity, id);
//                        }
//                    } else {
//                        throw new DataAccessException("ID failed to generate. No result returned.");
//                    }
//                }
//                cascadeInserts(
//                    annotationMetadata,
//                    repositoryType,
//                    insert,
//                    entity,
//                    persisted,
//                    connection,
//                    identity
//                );
//                return entity;
//            } catch (SQLException e) {
//                throw new DataAccessException("SQL Error executing INSERT: " + e.getMessage(), e);
//            }
//        });
    }

    private <T> void cascadeInserts(
        AnnotationMetadata annotationMetadata,
        Class<?> repositoryType,
        StoredInsert<T> insert,
        T entity,
        Set persisted,
        CqlSession connection,
        BeanProperty<T, Object> identity){
        if (identity != null) {

            final RuntimePersistentEntity<T> persistentEntity = (RuntimePersistentEntity<T>) getEntity(entity.getClass());
            for (RuntimeAssociation<T> association : persistentEntity.getAssociations()) {
                if (association.doesCascade(Relation.Cascade.PERSIST)) {
                    final Relation.Kind kind = association.getKind();
                    final RuntimePersistentEntity<?> associatedEntity =
                        association.getAssociatedEntity();
                    final Class<?> associationType = associatedEntity.getIntrospection().getBeanType();
                    final RuntimePersistentProperty<?> associatedId = associatedEntity.getIdentity();
                    final BeanProperty associatedIdProperty = associatedId.getProperty();
                    switch (kind) {
                        case ONE_TO_ONE:
                        case MANY_TO_ONE:
                            // in the case of an one-to-one we ensure the inverse side set
                            final Object associated = association.getProperty().get(entity);
                            if (associated == null || persisted.contains(associated)) {
                                continue;
                            }
                            if (association.isForeignKey()) {
                                association.getInverseSide().ifPresent(inverse -> {
                                    final BeanProperty property = inverse.getProperty();
                                    property.set(associated, entity);
                                });
                            }

                            // get the insert operation
                            StoredInsert associatedInsert = resolveEntityInsert(
                                annotationMetadata,
                                repositoryType,
                                associationType,
                                associatedEntity
                            );

                            if (associatedId != null) {
                                final Object id = associatedIdProperty.get(associated);
                                if (id != null) {
                                    continue;
                                }
                            }

                            persistOne(
                                annotationMetadata,
                                repositoryType,
                                associatedInsert,
                                associated,
                                persisted
                            );

                            break;
                        case ONE_TO_MANY:
                        case MANY_TO_MANY:
                        default:
                            final Object many = association.getProperty().get(entity);
                            final RuntimeAssociation<?> inverse
                                = association.getInverseSide().orElse(null);
                            associatedInsert  = resolveEntityInsert(
                                annotationMetadata,
                                repositoryType,
                                associationType,
                                associatedEntity
                            );
                            if (many instanceof Iterable) {
                                Iterable entities = (Iterable) many;
                                List toPersist = new ArrayList(15);
                                for (Object o : entities) {
                                    if (o == null || persisted.contains(o)) {
                                        continue;
                                    }

                                    if (inverse != null) {
                                        if (inverse.getKind() == Relation.Kind.MANY_TO_ONE) {
                                            final BeanProperty property = inverse.getProperty();
                                            property.set(o, entity);
                                        }
                                    }
                                    if (associatedId != null) {
                                        final BeanProperty bp = associatedIdProperty;
                                        final Object id = bp.get(o);
                                        if (id == null) {
                                            toPersist.add(o);
                                        }
                                    }
                                }
                                final Iterable batchResult;
                                if (insert.doesSupportBatch()) {
                                    batchResult =
                                        persistInBatch(
                                            annotationMetadata,
                                            repositoryType,
                                            toPersist,
                                            associatedInsert,
                                            persisted
                                        );
                                } else {
                                    final ArrayList<Object> arrayList = new ArrayList<>(toPersist);
                                    for (Object o : toPersist) {
                                        arrayList.add(persistOne(
                                            annotationMetadata,
                                            repositoryType,
                                            associatedInsert,
                                            o,
                                            persisted
                                        ));
                                    }
                                    batchResult = arrayList;
                                }

                                if (SqlQueryBuilder.isForeignKeyWithJoinTable(association)) {
                                    String associationInsert = resolveAssociationInsert(
                                        repositoryType,
                                        persistentEntity,
                                        association
                                    );

//
//                                    try (PreparedStatement ps =
//                                             connection.prepareStatement(associationInsert)) {
//                                        if (QUERY_LOG.isDebugEnabled()) {
//                                            QUERY_LOG.debug("Executing SQL Insert: {}", associationInsert);
//                                        }
//                                        final Object parentId = identity.get(entity);
//                                        for (Object o : batchResult) {
//                                            final Object childId = associatedIdProperty.get(o);
//                                            if (QUERY_LOG.isTraceEnabled()) {
//                                                QUERY_LOG.trace("Binding parameter at position {} to value {}", 1, parentId);
//                                            }
//                                            preparedStatementWriter.setDynamic(
//                                                ps,
//                                                1,
//                                                persistentEntity.getIdentity().getDataType(),
//                                                parentId);
//                                            if (QUERY_LOG.isTraceEnabled()) {
//                                                QUERY_LOG.trace("Binding parameter at position {} to value {}", 2, childId);
//                                            }
//                                            preparedStatementWriter.setDynamic(
//                                                ps,
//                                                2,
//                                                associatedId.getDataType(),
//                                                childId);
//                                            ps.addBatch();
//                                        }
//                                        ps.executeBatch();
//                                    }
                                }
                            }
                    }
                }
            }
        }
    }

    private <T, R> BoundStatementBuilder prepareStatement(
        CqlSession session,
        @NonNull PreparedQuery<T, R> preparedQuery,
        boolean isUpdate,
        boolean isSingleResult) {
        Object[] queryParameters = preparedQuery.getParameterArray();
        int[] parameterBinding = preparedQuery.getIndexedParameterBinding();
        DataType[] parameterTypes = preparedQuery.getIndexedParameterTypes();
        String query = preparedQuery.getQuery();

        final boolean hasIn = preparedQuery.hasInExpression();
        if (hasIn) {
            Matcher matcher = IN_EXPRESSION_PATTERN.matcher(query);
            // this has to be done is two passes, one to remove and establish new indexes
            // and again to expand existing indexes
            while (matcher.find()) {
                int inIndex = Integer.valueOf(matcher.group(1));
                int queryParameterIndex = parameterBinding[inIndex - 1];
                Object value = queryParameters[queryParameterIndex];

                if (value == null) {
                    query = matcher.replaceFirst(NOT_TRUE_EXPRESSION);
                    queryParameters[queryParameterIndex] = IGNORED_PARAMETER;
                } else {
                    int size = sizeOf(value);
                    if (size == 0) {
                        queryParameters[queryParameterIndex] = IGNORED_PARAMETER;
                        query = matcher.replaceFirst(NOT_TRUE_EXPRESSION);
                    } else {
                        String replacement = " IN(" + String.join(",", Collections.nCopies(size, "?")) + ")";
                        query = matcher.replaceFirst(replacement);
                    }
                }
                matcher = IN_EXPRESSION_PATTERN.matcher(query);
            }
        }

        if (!isUpdate) {
            Pageable pageable = preparedQuery.getPageable();
            if (pageable != Pageable.UNPAGED) {
                Class<T> rootEntity = preparedQuery.getRootEntity();
                Sort sort = pageable.getSort();
                final Class<?> repositoryType = preparedQuery.getRepositoryType();
                Dialect dialect = dialects.getOrDefault(repositoryType, Dialect.ANSI);
//                QueryBuilder queryBuilder = queryBuilders.getOrDefault(dialect, DEFAULT_SQL_BUILDER);
                if (sort.isSorted()) {
                    query += queryBuilder.buildOrderBy(getEntity(rootEntity), sort).getQuery();
                } else if (isSqlServerWithoutOrderBy(query, dialect)) {
                    // SQL server requires order by
                    RuntimePersistentEntity<T> persistentEntity = getEntity(rootEntity);
                    sort = sortById(persistentEntity);
                    query += queryBuilder.buildOrderBy(persistentEntity, sort).getQuery();
                }
                if (isSingleResult && pageable.getOffset() > 0) {
                    pageable = Pageable.from(pageable.getNumber(), 1);
                }
                query += queryBuilder.buildPagination(pageable).getQuery();
            }
        }

        if (QUERY_LOG.isDebugEnabled()) {
            QUERY_LOG.debug("Executing Query: {}", query);
        }
        final BoundStatementBuilder ps = session.prepare(query).boundStatementBuilder();
        int index = 1;
        for (int i = 0; i < parameterBinding.length; i++) {
            int parameterIndex = parameterBinding[i];
            DataType dataType = parameterTypes[i];
            Object value;
            if (parameterIndex > -1) {
                value = queryParameters[parameterIndex];
            } else {
                String[] indexedParameterPaths = preparedQuery.getIndexedParameterPaths();
                String propertyPath = indexedParameterPaths[i];
                if (propertyPath != null) {

                    String lastUpdatedProperty = preparedQuery.getLastUpdatedProperty();
                    if (lastUpdatedProperty != null && lastUpdatedProperty.equals(propertyPath)) {
                        Class<?> lastUpdatedType = preparedQuery.getLastUpdatedType();
                        if (lastUpdatedType == null) {
                            throw new IllegalStateException("Could not establish last updated time for entity: " + preparedQuery.getRootEntity());
                        }
                        Object timestamp = ConversionService.SHARED.convert(dateTimeProvider.getNow(), lastUpdatedType).orElse(null);
                        if (timestamp == null) {
                            throw new IllegalStateException("Unsupported date type: " + lastUpdatedType);
                        }
                        value = timestamp;
                    } else {
                        int j = propertyPath.indexOf('.');
                        if (j > -1) {
                            String subProp = propertyPath.substring(j + 1);
                            value = queryParameters[Integer.valueOf(propertyPath.substring(0, j))];
                            value = BeanWrapper.getWrapper(value).getRequiredProperty(subProp, Argument.OBJECT_ARGUMENT);
                        } else {
                            throw new IllegalStateException("Invalid query [" + query + "]. Unable to establish parameter value for parameter at position: " + (i + 1));
                        }
                    }
                } else {
                    throw new IllegalStateException("Invalid query [" + query + "]. Unable to establish parameter value for parameter at position: " + (i + 1));
                }
            }

            if (QUERY_LOG.isTraceEnabled()) {
                QUERY_LOG.trace("Binding parameter at position {} to value {}", index, value);
            }
            if (value == null) {
                setStatementParameter(ps, index++, dataType, null);
            } else if (value != IGNORED_PARAMETER) {
                if (value instanceof Iterable) {
                    Iterable iter = (Iterable) value;
                    for (Object o : iter) {
                        setStatementParameter(ps, index++, dataType, o);
                    }
                } else if (value.getClass().isArray()) {
                    int len = Array.getLength(value);
                    for (int j = 0; j < len; j++) {
                        Object o = Array.get(value, j);
                        setStatementParameter(ps, index++, dataType, o);
                    }
                } else {
                    setStatementParameter(ps, index++, dataType, value);
                }
            }
        }
        return ps;
    }

    @Nullable
    @Override
    public <T> T findOne(@NonNull Class<T> type, @NonNull Serializable id) {
        throw new UnsupportedOperationException("The findOne method by ID is not supported. Execute the SQL query directly");
    }

    @NonNull
    @Override
    public <T> Iterable<T> findAll(@NonNull PagedQuery<T> query) {
        throw new UnsupportedOperationException("The findAll method without an explicit query is not supported. Use findAll(PreparedQuery) instead");
    }

    @Override
    public <T> long count(PagedQuery<T> pagedQuery) {
        throw new UnsupportedOperationException("The count method without an explicit query is not supported. Use findAll(PreparedQuery) instead");
    }

    @NonNull
    @Override
    public <T> Stream<T> findStream(@NonNull PagedQuery<T> query) {
        throw new UnsupportedOperationException("The findStream method without an explicit query is not supported. Use findStream(PreparedQuery) instead");
    }

    @Override
    public <R> Page<R> findPage(@NonNull PagedQuery<R> query) {
        throw new UnsupportedOperationException("The findPage method without an explicit query is not supported. Use findPage(PreparedQuery) instead");
    }

    @NonNull
    @Override
    public <T> Iterable<T> persistAll(@NonNull BatchOperation<T> operation) {
        @SuppressWarnings("unchecked") StoredInsert<T> insert = resolveInsert(operation);
        if (!insert.doesSupportBatch()) {
            List<T> results = new ArrayList<>();
            for (T entity : operation) {
                results.add(persist(new InsertOperation<T>() {
                    @NonNull
                    @Override
                    public T getEntity() {
                        return entity;
                    }

                    @NonNull
                    @Override
                    public Class<T> getRootEntity() {
                        return operation.getRootEntity();
                    }

                    @NonNull
                    @Override
                    public Class<?> getRepositoryType() {
                        return operation.getRepositoryType();
                    }

                    @Override
                    public String getName() {
                        return operation.getName();
                    }

                    @Override
                    public AnnotationMetadata getAnnotationMetadata() {
                        return operation.getAnnotationMetadata();
                    }
                }));
            }
            return results;
        } else {
            //noinspection ConstantConditions
            return persistInBatch(
                operation.getAnnotationMetadata(),
                operation.getRepositoryType(),
                operation,
                insert,
                new HashSet(10)
            );
        }
    }

    private <T> Iterable<T> persistInBatch(
        AnnotationMetadata annotationMetadata,
        Class<?> repositoryType,
        @NonNull Iterable<T> entities,
        StoredInsert<T> insert,
        Set persisted) {

        List<T> payload = new ArrayList<>(10);
        boolean generateId = insert.isGenerateId();
        String insertSql = insert.getSql();

        if (QUERY_LOG.isDebugEnabled()) {
            QUERY_LOG.debug("Executing Batch SQL Insert: {}", insertSql);
        }
        PreparedStatement stmt = this.cqlSession.prepare(insertSql);
        BatchStatementBuilder batchBuilder = BatchStatement.builder(DefaultBatchType.LOGGED);
        for (T entity : entities) {
            if(persisted.contains(entity)){
                continue;
            }
            BoundStatementBuilder builder = stmt.boundStatementBuilder();
            setInsertParameters(insert,entity,builder);
            batchBuilder.addStatement(builder.build());
            payload.add(entity);
        }
        ResultSet results = this.cqlSession.execute(batchBuilder.build());
        for(Row row : results){

        }
        return payload;


//        return transactionOperations.executeWrite((status) -> {
//            Connection connection = status.getConnection();
//            List<T> results = new ArrayList<>(10);
//            boolean generateId = insert.isGenerateId();
//            String insertSql = insert.getSql();
//            BeanProperty<T, Object> identity = insert.getIdentityProperty();
//            final boolean hasGeneratedID = generateId && identity != null;
//
//
//            try {
//                PreparedStatement stmt;
//                if (hasGeneratedID && insert.getDialect() == Dialect.ORACLE) {
//                    stmt = connection
//                        .prepareStatement(insertSql, new String[] { identity.getName() });
//                } else {
//                    stmt = connection
//                        .prepareStatement(insertSql, generateId ? java.sql.Statement.RETURN_GENERATED_KEYS : java.sql.Statement.NO_GENERATED_KEYS);
//                }
//                if (QUERY_LOG.isDebugEnabled()) {
//                    QUERY_LOG.debug("Executing Batch SQL Insert: {}", insertSql);
//                }
//                for (T entity : entities) {
//                    if (persisted.contains(entity)) {
//                        continue;
//                    }
//                    setInsertParameters(insert, entity, stmt);
//                    stmt.addBatch();
//                    results.add(entity);
//                }
//                stmt.executeBatch();
//
//
//                if (hasGeneratedID) {
//                    Iterator<T> resultIterator = results.iterator();
//                    java.sql.ResultSet generatedKeys = stmt.getGeneratedKeys();
//                    while (resultIterator.hasNext()) {
//                        T entity = resultIterator.next();
//                        if (!generatedKeys.next()) {
//                            throw new DataAccessException("Failed to generate ID for entity: " + entity);
//                        } else {
//                            long id = generatedKeys.getLong(1);
//                            if (identity.getType().isInstance(id)) {
//                                identity.set(entity, id);
//                            } else {
//                                identity.convertAndSet(entity, id);
//                            }
//                        }
//                    }
//                }
//                for (T result : results) {
//                    cascadeInserts(
//                        annotationMetadata,
//                        repositoryType,
//                        insert,
//                        result,
//                        persisted,
//                        connection,
//                        identity
//                    );
//                }
//                return results;
//            } catch (SQLException e) {
//                throw new DataAccessException("SQL error executing INSERT: " + e.getMessage(), e);
//            }
//        });
    }

//    @Override
//    @PreDestroy
//    public void close() {
//        if (executorService != null) {
//            executorService.shutdown();
//        }
//    }
//
//    @NonNull
//    @Override
//    public DataSource getDataSource() {
//        return dataSource;
//    }
//
//    @NonNull
//    @Override
//    public Connection getConnection() {
//        return transactionOperations.getConnection();
//    }
//
//    @NonNull
//    @Override
//    public <R> R execute(@NonNull ConnectionCallback<R> callback) {
//        try {
//            return callback.call(transactionOperations.getConnection());
//        } catch (SQLException e) {
//            throw new DataAccessException("Error executing SQL Callback: " + e.getMessage(), e);
//        }
//    }
//
//    @NonNull
//    @Override
//    public <R> R prepareStatement(@NonNull String sql, @NonNull PreparedStatementCallback<R> callback) {
//        ArgumentUtils.requireNonNull("sql", sql);
//        ArgumentUtils.requireNonNull("callback", callback);
//        if (QUERY_LOG.isDebugEnabled()) {
//            QUERY_LOG.debug("Executing Query: {}", sql);
//        }
//        try {
//            return callback.call(transactionOperations.getConnection().prepareStatement(sql));
//        } catch (SQLException e) {
//            throw new DataAccessException("Error preparing SQL statement: " + e.getMessage(), e);
//        }
//    }
//
//    @NonNull
//    @Override
//    public <T> Stream<T> entityStream(@NonNull java.sql.ResultSet resultSet, @NonNull Class<T> rootEntity) {
//        return entityStream(resultSet, null, rootEntity);
//    }
//
//    @NonNull
//    @Override
//    public <E> E readEntity(@NonNull String prefix, @NonNull java.sql.ResultSet resultSet, @NonNull Class<E> type) throws DataAccessException {
//        return new SqlResultEntityTypeMapper<>(
//            prefix,
//            getEntity(type),
//            columnNameResultSetReader,
//            jsonCodec
//        ).map(resultSet, type);
//    }
//
//    @NonNull
//    @Override
//    public <E, D> D readDTO(@NonNull String prefix, @NonNull java.sql.ResultSet resultSet, @NonNull Class<E> rootEntity, @NonNull Class<D> dtoType) throws DataAccessException {
//        return new DTOMapper<E, java.sql.ResultSet, D>(
//            getEntity(rootEntity),
//            columnNameResultSetReader
//        ).map(resultSet, dtoType);
//    }
//
//    @NonNull
//    @Override
//    public <T> Stream<T> entityStream(@NonNull java.sql.ResultSet resultSet, @Nullable String prefix, @NonNull Class<T> rootEntity) {
//        ArgumentUtils.requireNonNull("resultSet", resultSet);
//        ArgumentUtils.requireNonNull("rootEntity", rootEntity);
//        TypeMapper<java.sql.ResultSet, T> mapper = new SqlResultEntityTypeMapper<>(prefix, getEntity(rootEntity), columnNameResultSetReader, jsonCodec);
//        Iterable<T> iterable = () -> new Iterator<T>() {
//            boolean nextCalled = false;
//
//            @Override
//            public boolean hasNext() {
//                try {
//                    if (!nextCalled) {
//                        nextCalled = true;
//                        return resultSet.next();
//                    } else {
//                        return nextCalled;
//                    }
//                } catch (SQLException e) {
//                    throw new DataAccessException("Error retrieving next JDBC result: " + e.getMessage(), e);
//                }
//            }
//
//            @Override
//            public T next() {
//                nextCalled = false;
//                return mapper.map(resultSet, rootEntity);
//            }
//        };
//        return StreamSupport.stream(iterable.spliterator(), false);
//    }
}
