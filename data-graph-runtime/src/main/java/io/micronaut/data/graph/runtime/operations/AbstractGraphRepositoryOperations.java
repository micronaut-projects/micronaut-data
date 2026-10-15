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
package io.micronaut.data.graph.runtime.operations;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.graph.annotation.TargetNode;
import io.micronaut.data.graph.model.GraphEntities;
import io.micronaut.data.graph.model.GraphPath;
import io.micronaut.data.graph.model.GraphRelationship;
import io.micronaut.data.graph.model.query.PathLimitParameter;
import io.micronaut.data.graph.model.query.PathQueryDefinition;
import io.micronaut.data.graph.model.query.builder.CypherQueryBuilder;
import io.micronaut.data.graph.model.query.builder.GraphQueryBuilder;
import io.micronaut.data.graph.runtime.GraphEdge;
import io.micronaut.data.graph.runtime.GraphEntityMapper;
import io.micronaut.data.graph.runtime.GraphNode;
import io.micronaut.data.graph.runtime.GraphPathValue;
import io.micronaut.data.graph.runtime.GraphRecord;
import io.micronaut.data.graph.runtime.GraphResult;
import io.micronaut.data.graph.runtime.GraphSession;
import io.micronaut.data.graph.runtime.GraphValueEncoderRegistrar;
import io.micronaut.data.graph.runtime.GraphValueEncoders;
import io.micronaut.data.model.Limit;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.AttributeConverterRegistry;
import io.micronaut.data.model.runtime.DeleteBatchOperation;
import io.micronaut.data.model.runtime.DeleteOperation;
import io.micronaut.data.model.runtime.InsertBatchOperation;
import io.micronaut.data.model.runtime.InsertOperation;
import io.micronaut.data.model.runtime.PagedQuery;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.RuntimeAssociation;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.model.runtime.UpdateBatchOperation;
import io.micronaut.data.model.runtime.UpdateOperation;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import io.micronaut.data.operations.async.AsyncCapableRepository;
import io.micronaut.data.operations.reactive.ReactiveCapableRepository;
import io.micronaut.data.operations.reactive.ReactiveRepositoryOperations;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.date.DateTimeProvider;
import io.micronaut.data.runtime.operations.ExecutorAsyncOperations;
import io.micronaut.data.runtime.operations.ExecutorReactiveOperations;
import io.micronaut.data.runtime.operations.internal.AbstractRepositoryOperations;
import io.micronaut.data.runtime.operations.internal.AbstractSyncEntityOperations;
import io.micronaut.data.runtime.operations.internal.ExecutorServiceResolver;
import io.micronaut.data.runtime.operations.internal.OperationContext;
import io.micronaut.data.runtime.operations.internal.SyncCascadeOperations;
import io.micronaut.data.runtime.operations.internal.SynchronizedLazyValue;
import io.micronaut.data.runtime.operations.internal.query.BindableParametersPreparedQuery;
import io.micronaut.data.runtime.operations.internal.query.BindableParametersStoredQuery;
import io.micronaut.data.runtime.operations.internal.query.DefaultBindableParametersPreparedQuery;
import io.micronaut.data.runtime.operations.internal.query.DefaultBindableParametersStoredQuery;
import io.micronaut.data.runtime.query.MethodContextAwareStoredQueryDecorator;
import io.micronaut.data.runtime.query.PreparedQueryDecorator;
import io.micronaut.data.annotation.Relation;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Repository operations shared by graph databases that speak Cypher. A database module implements how queries
 * run within the current connection or transaction, and how values are decoded.
 *
 * @author graemerocher
 * @since 5.3.0
 */
public abstract class AbstractGraphRepositoryOperations extends AbstractRepositoryOperations implements
    GraphRepositoryOperations,
    PreparedQueryDecorator,
    MethodContextAwareStoredQueryDecorator,
    AsyncCapableRepository,
    ReactiveCapableRepository,
    AutoCloseable {
    protected final GraphQueryBuilder queryBuilder;

    protected final GraphEntityMapper mapper;

    private final ExecutorServiceResolver executorServiceResolver;

    private final SynchronizedLazyValue<ExecutorAsyncOperations> asyncOperations = new SynchronizedLazyValue<>();

    private final Map<String, String> queryCache = new ConcurrentHashMap<>();

    private final GraphValueEncoders encoders = GraphValueEncoders.defaults();

    private final SyncCascadeOperations<OperationContext> noCascade;

    /**
     * @param queryBuilder               The query builder of the dialect
     * @param dateTimeProvider           The date time provider
     * @param runtimeEntityRegistry      The entity registry
     * @param conversionService          The conversion service
     * @param attributeConverterRegistry The attribute converter registry
     * @param executorService            The executor for asynchronous and reactive operations
     * @param encoderRegistrars          The registrars of the encoders of the application
     */
    protected AbstractGraphRepositoryOperations(GraphQueryBuilder queryBuilder,
                                                DateTimeProvider<Object> dateTimeProvider,
                                                RuntimeEntityRegistry runtimeEntityRegistry,
                                                DataConversionService conversionService,
                                                AttributeConverterRegistry attributeConverterRegistry,
                                                @Nullable ExecutorService executorService,
                                                List<GraphValueEncoderRegistrar> encoderRegistrars) {
        super(dateTimeProvider, runtimeEntityRegistry, conversionService, attributeConverterRegistry);
        this.queryBuilder = queryBuilder;
        registerEncoders(encoders);
        for (GraphValueEncoderRegistrar registrar : encoderRegistrars) {
            registrar.register(encoders);
        }
        this.mapper = new GraphEntityMapper(runtimeEntityRegistry, conversionService, this::encodeValue);
        this.executorServiceResolver = new ExecutorServiceResolver(executorService);
        this.noCascade = new SyncCascadeOperations<>(conversionService, new RelationshipsWrittenByNode());
    }

    /**
     * Runs work that only reads, within the current connection or a new one.
     *
     * @param annotationMetadata The annotation metadata of the operation
     * @param work               The work
     * @param <R>                The result type
     * @return The result
     */
    protected abstract <R> R read(AnnotationMetadata annotationMetadata, Function<GraphSession, R> work);

    /**
     * Runs work that writes, within the current transaction or a new connection.
     *
     * @param annotationMetadata The annotation metadata of the operation
     * @param work               The work
     * @param <R>                The result type
     * @return The result
     */
    protected abstract <R> R write(AnnotationMetadata annotationMetadata, Function<GraphSession, R> work);

    /**
     * Registers the encoders of the database, which replace or add to the defaults. Called once, while the operations
     * are constructed, before the encoders of the application are registered.
     *
     * @param encoders The encoders
     */
    protected void registerEncoders(GraphValueEncoders encoders) {
    }

    /**
     * Encodes a Java value into a value the database accepts as a parameter or property.
     *
     * @param value The value, never null
     * @return The encoded value
     */
    protected final Object encodeValue(Object value) {
        return Objects.requireNonNull(encoders.encode(value));
    }

    @Override
    public <E, R> PreparedQuery<E, R> decorate(PreparedQuery<E, R> preparedQuery) {
        return new DefaultBindableParametersPreparedQuery<>(preparedQuery);
    }

    @Override
    public <E, R> StoredQuery<E, R> decorate(MethodInvocationContext<?, ?> context, StoredQuery<E, R> storedQuery) {
        RuntimePersistentEntity<E> entity = runtimeEntityRegistry.getEntity(storedQuery.getRootEntity());
        return new DefaultBindableParametersStoredQuery<>(storedQuery, entity, conversionService);
    }

    @Override
    public ExecutorAsyncOperations async() {
        return asyncOperations.get(() -> new ExecutorAsyncOperations(this, executorServiceResolver.get()));
    }

    @Override
    public ReactiveRepositoryOperations reactive() {
        return new ExecutorReactiveOperations(async(), conversionService);
    }

    @Override
    public void close() {
        executorServiceResolver.close();
    }

    // Queries

    @Override
    public @Nullable <T> T findOne(Class<T> type, Object id) {
        RuntimePersistentEntity<T> entity = getEntity(type);
        String query = queryCache.computeIfAbsent("findById:" + type.getName(), k -> queryBuilder.buildFindByIdQuery(entity));
        Object encodedId = mapper.writeValue(entity.getIdentity(), id);
        return read(entity.getAnnotationMetadata(), session -> {
            List<GraphRecord> records = session.run(query, Map.of(CypherQueryBuilder.ID_PARAMETER, encodedId)).records();
            if (records.isEmpty()) {
                return null;
            }
            return readEntity(entity, records.get(0), entity.getAnnotationMetadata());
        });
    }

    @Override
    public @Nullable <T, R> R findOne(PreparedQuery<T, R> preparedQuery) {
        GraphQuery query = prepare(preparedQuery, true);
        return read(preparedQuery.getAnnotationMetadata(), session -> {
            List<GraphRecord> records = session.run(query.query(), query.parameters()).records();
            if (records.isEmpty()) {
                return null;
            }
            return mapRecord(preparedQuery, records.get(0));
        });
    }

    @Override
    public <T> boolean exists(PreparedQuery<T, Boolean> preparedQuery) {
        GraphQuery query = prepare(preparedQuery, false);
        return read(preparedQuery.getAnnotationMetadata(), session -> {
            List<GraphRecord> records = session.run(query.query(), query.parameters()).records();
            if (records.isEmpty()) {
                return false;
            }
            GraphRecord first = records.get(0);
            return first.size() == 0 || !(first.get(0) instanceof Boolean b) || b;
        });
    }

    @Override
    public <T> Iterable<T> findAll(PagedQuery<T> query) {
        RuntimePersistentEntity<T> entity = getEntity(query.getRootEntity());
        StringBuilder cypher = new StringBuilder(queryBuilder.buildFindAllQuery(entity));
        appendSortAndLimit(cypher, entity, query.getSort(), query.getQueryLimit());
        return read(query.getAnnotationMetadata(), session -> session.run(cypher.toString(), Map.of()).records()
            .stream()
            .map(row -> readEntity(entity, row, query.getAnnotationMetadata()))
            .toList());
    }

    @Override
    public <T> long count(PagedQuery<T> pagedQuery) {
        RuntimePersistentEntity<T> entity = getEntity(pagedQuery.getRootEntity());
        String query = queryBuilder.buildCountAllQuery(entity);
        return read(pagedQuery.getAnnotationMetadata(), session -> {
            List<GraphRecord> records = session.run(query, Map.of()).records();
            return !records.isEmpty() && records.get(0).get(0) instanceof Number number ? number.longValue() : 0L;
        });
    }

    @Override
    public <T, R> Iterable<R> findAll(PreparedQuery<T, R> preparedQuery) {
        return findList(preparedQuery);
    }

    @Override
    public <R> List<R> execute(PreparedQuery<?, R> preparedQuery) {
        return findList(preparedQuery);
    }

    private <T, R> List<R> findList(PreparedQuery<T, R> preparedQuery) {
        GraphQuery query = prepare(preparedQuery, true);
        return read(preparedQuery.getAnnotationMetadata(), session -> {
            List<GraphRecord> records = session.run(query.query(), query.parameters()).records();
            List<R> results = new ArrayList<>(records.size());
            for (GraphRecord record : records) {
                R result = mapRecord(preparedQuery, record);
                if (result != null) {
                    results.add(result);
                }
            }
            return results;
        });
    }

    @Override
    public <T, R> Stream<R> findStream(PreparedQuery<T, R> preparedQuery) {
        return findList(preparedQuery).stream();
    }

    @Override
    public <T> Stream<T> findStream(PagedQuery<T> query) {
        List<T> list = new ArrayList<>();
        findAll(query).forEach(list::add);
        return list.stream();
    }

    @Override
    public <R> Page<R> findPage(PagedQuery<R> query) {
        if (query instanceof PreparedQuery<?, ?> preparedQuery) {
            // A finder with a Pageable: its own query reads the page, and the interceptor runs its count query
            @SuppressWarnings("unchecked")
            List<R> page = findList((PreparedQuery<Object, R>) preparedQuery);
            return Page.of(page, query.getPageable(), null);
        }
        List<R> content = new ArrayList<>();
        findAll(query).forEach(content::add);
        Pageable pageable = query.getPageable();
        return Page.of(content, pageable, pageable.requestTotal() ? count(query) : null);
    }

    @Override
    public Optional<Number> executeUpdate(PreparedQuery<?, Number> preparedQuery) {
        GraphQuery query = prepare(preparedQuery, false);
        return Optional.of(write(preparedQuery.getAnnotationMetadata(), session -> updateCount(session.run(query.query(), query.parameters()))));
    }

    @Override
    public Optional<Number> executeDelete(PreparedQuery<?, Number> preparedQuery) {
        return executeUpdate(preparedQuery);
    }

    private static Number updateCount(GraphResult result) {
        if (!result.records().isEmpty()) {
            GraphRecord first = result.records().get(0);
            if (first.size() > 0 && first.get(0) instanceof Number number) {
                return number;
            }
        }
        return Math.max(result.updateCount(), 0);
    }

    @Override
    public List<GraphRecord> executeQuery(String query, Map<String, Object> parameters) {
        Map<String, Object> encoded = encodeParameters(parameters);
        String statement = finishedQuery(query);
        return write(AnnotationMetadata.EMPTY_METADATA, session -> session.run(statement, encoded).records());
    }

    @Override
    public <T> List<T> executeQuery(String query, Map<String, Object> parameters, Class<T> type) {
        List<GraphRecord> records = executeQuery(query, parameters);
        List<T> results = new ArrayList<>(records.size());
        for (GraphRecord record : records) {
            results.add(type.cast(mapValue(record, record.size() == 0 ? null : record.get(0), type, type, AnnotationMetadata.EMPTY_METADATA)));
        }
        return results;
    }

    private Map<String, Object> encodeParameters(Map<String, Object> parameters) {
        Map<String, Object> encoded = new LinkedHashMap<>();
        parameters.forEach((k, v) -> encoded.put(k, v == null ? null : encodeValue(v)));
        return encoded;
    }

    /**
     * Binds the parameters of a prepared query and applies sorting, limits and the path limit.
     *
     * @param preparedQuery The prepared query
     * @param applyLimits   Whether to apply sorting and limits from the method arguments
     * @return The query
     */
    protected GraphQuery prepare(PreparedQuery<?, ?> preparedQuery, boolean applyLimits) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        if (preparedQuery instanceof BindableParametersPreparedQuery<?, ?> bindable) {
            bindable.bindParameters(new MapBinder(parameters));
        }
        StringBuilder query = new StringBuilder(preparedQuery.getQuery());
        int limitIndex = preparedQuery.getAnnotationMetadata().intValue(PathLimitParameter.class).orElse(-1);
        int tokenPosition = query.indexOf(PathQueryDefinition.PATH_LIMIT_TOKEN);
        if (tokenPosition >= 0) {
            if (limitIndex < 0) {
                throw new DataAccessException("Path query has no @PathLimit argument: " + query);
            }
            Object limit = preparedQuery.getParameterArray()[limitIndex];
            int depth = limit instanceof Number number ? number.intValue() : -1;
            if (depth < 1) {
                throw new IllegalArgumentException("The @PathLimit argument must be at least 1, was: " + limit);
            }
            query.replace(tokenPosition, tokenPosition + PathQueryDefinition.PATH_LIMIT_TOKEN.length(), String.valueOf(depth));
        }
        if (applyLimits && !preparedQuery.isCount()) {
            RuntimePersistentEntity<?> entity = getEntity(preparedQuery.getRootEntity());
            appendSortAndLimit(query, entity, preparedQuery.getSort(), preparedQuery.getQueryLimit());
        }
        return new GraphQuery(query.toString(), parameters);
    }

    /**
     * Appends the ordering and limits requested by method arguments to a query.
     *
     * @param query  The query
     * @param entity The root entity
     * @param sort   The sort
     * @param limit  The limit
     */
    protected void appendSortAndLimit(StringBuilder query, RuntimePersistentEntity<?> entity, Sort sort, Limit limit) {
        boolean rootQuery = query.indexOf("MATCH (" + CypherQueryBuilder.ROOT) == 0;
        if (sort != null && sort.isSorted() && rootQuery && query.indexOf(" ORDER BY ") < 0) {
            appendOrderBy(query, entity, sort);
        }
        if (limit != null && limit.isLimited() && query.indexOf(" LIMIT ") < 0 && query.indexOf(" SKIP ") < 0) {
            query.append(queryBuilder.buildLimitAndOffset(limit.maxResults(), limit.offset()));
        }
    }

    private void appendOrderBy(StringBuilder query, RuntimePersistentEntity<?> entity, Sort sort) {
        query.append(" ORDER BY ");
        Iterator<Sort.Order> iterator = sort.getOrderBy().iterator();
        while (iterator.hasNext()) {
            Sort.Order order = iterator.next();
            String expression = CypherQueryBuilder.ROOT + "." + GraphEntities.escapeName(sortProperty(entity, order.getProperty()).getPersistedName());
            if (order.isIgnoreCase()) {
                expression = "toLower(" + expression + ")";
            }
            query.append(expression).append(order.isAscending() ? " ASC" : " DESC");
            if (iterator.hasNext()) {
                query.append(", ");
            }
        }
    }

    private RuntimePersistentProperty<?> sortProperty(RuntimePersistentEntity<?> entity, String name) {
        RuntimePersistentProperty<?> property = entity.getPropertyByName(name);
        if (property == null && entity.hasIdentity() && entity.getIdentity().getName().equals(name)) {
            property = entity.getIdentity();
        }
        if (property == null) {
            throw new IllegalArgumentException("Cannot sort on non-existent property: " + name);
        }
        return property;
    }

    // Result mapping

    private <R> @Nullable R mapRecord(PreparedQuery<?, R> preparedQuery, GraphRecord record) {
        Object first = record.size() == 0 ? null : record.get(0);
        Argument<R> resultArgument = preparedQuery.getResultArgument();
        Class<R> resultType = preparedQuery.getResultType();
        if (preparedQuery.isDtoProjection()) {
            Map<String, Object> values = record.size() == 1 && first instanceof GraphNode node ? node.properties() : record.asMap();
            return readDto(values, resultType);
        }
        Class<?> nodeType = nodeType(resultArgument, preparedQuery.getRootEntity());
        return (R) mapValue(record, first, resultType, nodeType, targetNodeType(resultArgument, nodeType), preparedQuery.getAnnotationMetadata());
    }

    private @Nullable Object mapValue(GraphRecord record, @Nullable Object first, Class<?> resultType, Class<?> nodeType, AnnotationMetadata annotationMetadata) {
        return mapValue(record, first, resultType, nodeType, nodeType, annotationMetadata);
    }

    /**
     * Maps a row to the result type of a method.
     *
     * @param record             The row
     * @param first              The first value of the row, or null
     * @param resultType         The result type
     * @param nodeType           The type of the nodes in the result, and of the start node of a relationship
     * @param targetNodeType     The type of the end node of a relationship
     * @param annotationMetadata The annotation metadata of the method
     * @return The value
     */
    private @Nullable Object mapValue(GraphRecord record, @Nullable Object first, Class<?> resultType, Class<?> nodeType,
                                      Class<?> targetNodeType, AnnotationMetadata annotationMetadata) {
        if (resultType == GraphRecord.class) {
            return record;
        }
        if (GraphPath.class.isAssignableFrom(resultType)) {
            GraphPathValue path = firstValue(record, GraphPathValue.class);
            return path == null ? null : mapper.readPath(path, nodeType);
        }
        if (GraphRelationship.class.isAssignableFrom(resultType)) {
            return mapRelationship(record, nodeType, targetNodeType);
        }
        if (first instanceof GraphNode node) {
            return mapNode(record, node, resultType, annotationMetadata);
        }
        if (first instanceof GraphPathValue path) {
            return mapper.readPath(path, nodeType);
        }
        if (resultType == Map.class && record.size() > 1) {
            return record.asMap();
        }
        return mapper.convert(first, Argument.of(resultType));
    }

    private @Nullable Object mapRelationship(GraphRecord record, Class<?> nodeType, Class<?> targetNodeType) {
        GraphEdge edge = record.get(CypherQueryBuilder.PATH_RELATIONSHIP) instanceof GraphEdge e ? e : firstValue(record, GraphEdge.class);
        if (edge == null) {
            return null;
        }
        GraphNode from = (GraphNode) record.get(CypherQueryBuilder.RELATIONSHIP_FROM);
        GraphNode to = (GraphNode) record.get(CypherQueryBuilder.RELATIONSHIP_TO);
        if (from == null || to == null) {
            throw new DataAccessException("A query returning relationships must also return their start and end nodes as columns f and t");
        }
        return mapper.readRelationship(edge, from, to, nodeType, targetNodeType);
    }

    private Object mapNode(GraphRecord record, GraphNode node, Class<?> resultType, AnnotationMetadata annotationMetadata) {
        if (resultType != Object.class && isEntity(resultType)) {
            RuntimePersistentEntity<?> entity = getEntity(resultType);
            if (node.labels().contains(GraphEntities.primaryLabel(entity))) {
                return readEntity(entity, record, annotationMetadata);
            }
        }
        return mapper.readNodeAs(node, resultType);
    }

    private <T> T readEntity(RuntimePersistentEntity<T> entity, GraphRecord record, AnnotationMetadata annotationMetadata) {
        Object first = record.get(CypherQueryBuilder.ROOT);
        if (!(first instanceof GraphNode)) {
            first = record.size() == 0 ? null : record.get(0);
        }
        if (!(first instanceof GraphNode node)) {
            throw new DataAccessException("Expected a node for entity [" + entity.getName() + "], found: " + first);
        }
        T instance = mapper.readNode(entity, node, record);
        return triggerPostLoad(instance, entity, annotationMetadata);
    }

    private static boolean isEntity(Class<?> type) {
        return BeanIntrospector.SHARED.findIntrospection(type)
            .map(introspection -> introspection.hasStereotype(io.micronaut.data.annotation.MappedEntity.class))
            .orElse(false);
    }

    /**
     * The type of the end node of a relationship result: the second type argument of {@code GraphRelationship<F, T>},
     * or the type of its start node.
     *
     * @param resultArgument The result argument
     * @param nodeType       The type of the start node
     * @return The type
     */
    private static Class<?> targetNodeType(Argument<?> resultArgument, Class<?> nodeType) {
        Argument<?>[] typeParameters = resultArgument.getTypeParameters();
        if (GraphRelationship.class.isAssignableFrom(resultArgument.getType()) && typeParameters.length > 1) {
            return typeParameters[1].getType();
        }
        return nodeType;
    }

    private static Class<?> nodeType(Argument<?> resultArgument, Class<?> rootEntity) {
        Argument<?>[] typeParameters = resultArgument.getTypeParameters();
        if (typeParameters.length > 0) {
            return typeParameters[0].getType();
        }
        return rootEntity;
    }

    private static <V> @Nullable V firstValue(GraphRecord record, Class<V> type) {
        for (Object value : record.values()) {
            if (type.isInstance(value)) {
                return type.cast(value);
            }
        }
        return null;
    }

    private <R> R readDto(Map<String, Object> values, Class<R> type) {
        BeanIntrospection<R> introspection = BeanIntrospection.getIntrospection(type);
        Argument<Object>[] constructorArguments = (Argument<Object>[]) introspection.getConstructorArguments();
        R instance;
        if (constructorArguments.length == 0) {
            instance = introspection.instantiate();
        } else {
            Object[] arguments = new Object[constructorArguments.length];
            for (int i = 0; i < constructorArguments.length; i++) {
                arguments[i] = mapper.convert(values.get(constructorArguments[i].getName()), constructorArguments[i]);
            }
            instance = introspection.instantiate(false, arguments);
        }
        for (BeanProperty<R, Object> property : introspection.getBeanProperties()) {
            if (!property.isReadOnly() && values.containsKey(property.getName())) {
                property.set(instance, mapper.convert(values.get(property.getName()), property.asArgument()));
            }
        }
        return instance;
    }

    // Writes

    @Override
    public <T> T persist(InsertOperation<T> operation) {
        return write(operation.getAnnotationMetadata(), session -> new Persister(session, operation.getAnnotationMetadata(), operation.getRepositoryType())
            .save(getEntity(operation.getRootEntity()), operation.getEntity(), true));
    }

    @Override
    public <T> Iterable<T> persistAll(InsertBatchOperation<T> operation) {
        return write(operation.getAnnotationMetadata(), session -> {
            Persister persister = new Persister(session, operation.getAnnotationMetadata(), operation.getRepositoryType());
            RuntimePersistentEntity<T> entity = getEntity(operation.getRootEntity());
            List<T> results = new ArrayList<>();
            for (T instance : operation) {
                results.add(persister.save(entity, instance, true));
            }
            return results;
        });
    }

    @Override
    public <T> T update(UpdateOperation<T> operation) {
        return write(operation.getAnnotationMetadata(), session -> new Persister(session, operation.getAnnotationMetadata(), operation.getRepositoryType())
            .save(getEntity(operation.getRootEntity()), operation.getEntity(), false));
    }

    @Override
    public <T> Iterable<T> updateAll(UpdateBatchOperation<T> operation) {
        return write(operation.getAnnotationMetadata(), session -> {
            Persister persister = new Persister(session, operation.getAnnotationMetadata(), operation.getRepositoryType());
            RuntimePersistentEntity<T> entity = getEntity(operation.getRootEntity());
            List<T> results = new ArrayList<>();
            for (T instance : operation) {
                results.add(persister.save(entity, instance, false));
            }
            return results;
        });
    }

    @Override
    public <T> int delete(DeleteOperation<T> operation) {
        return write(operation.getAnnotationMetadata(), session -> new Persister(session, operation.getAnnotationMetadata(), operation.getRepositoryType())
            .delete(getEntity(operation.getRootEntity()), operation.getEntity()));
    }

    @Override
    public <T> Optional<Number> deleteAll(DeleteBatchOperation<T> operation) {
        RuntimePersistentEntity<T> entity = getEntity(operation.getRootEntity());
        if (operation.all()) {
            String query = deleteAllQuery(entity);
            return Optional.of(write(operation.getAnnotationMetadata(), session -> updateCount(session.run(query, Map.of()))));
        }
        return Optional.of(write(operation.getAnnotationMetadata(), session -> {
            Persister persister = new Persister(session, operation.getAnnotationMetadata(), operation.getRepositoryType());
            int count = 0;
            for (T instance : operation) {
                count += persister.delete(entity, instance);
            }
            return count;
        }));
    }

    private boolean isNew(RuntimePersistentEntity<?> entity, Object instance) {
        RuntimePersistentProperty<?> identity = entity.getIdentity();
        if (identity == null) {
            return true;
        }
        // A related node with an identifier was saved before: update it rather than generate a new identifier
        return ((BeanProperty<Object, Object>) identity.getProperty()).get(instance) == null;
    }

    private static <B> B withProperty(BeanProperty<B, Object> property, B bean, @Nullable Object value) {
        if (property.isReadOnly()) {
            return property.withValue(bean, value);
        }
        property.set(bean, value);
        return bean;
    }

    private static RuntimeAssociation<?> targetNode(RuntimePersistentEntity<?> relationshipEntity) {
        for (RuntimePersistentProperty<?> property : relationshipEntity.getPersistentProperties()) {
            if (property instanceof RuntimeAssociation<?> association && property.getAnnotationMetadata().hasStereotype(TargetNode.class)) {
                return association;
            }
        }
        throw new DataAccessException("Relationship entity [" + relationshipEntity.getName() + "] has no @TargetNode property");
    }

    /**
     * @param entity The root entity
     * @return The query that deletes all nodes of the entity, returning their count
     */
    final String deleteAllQuery(RuntimePersistentEntity<?> entity) {
        return queryCache.computeIfAbsent("deleteAll:" + entity.getName(), k -> queryBuilder.buildDeleteAllQuery(entity));
    }

    /**
     * @param query A query in the database's query language, passed to the operations at runtime
     * @return The statement that runs it
     */
    final String finishedQuery(String query) {
        return queryCache.computeIfAbsent("query:" + query, k -> queryBuilder.finishQuery(query));
    }

    /**
     * A query ready to run.
     *
     * @param query      The query text
     * @param parameters The encoded parameters
     */
    protected record GraphQuery(String query, Map<String, Object> parameters) {
    }

    /**
     * Collects bound parameters into a map keyed by parameter name.
     */
    private final class MapBinder implements BindableParametersStoredQuery.Binder {
        private final Map<String, Object> parameters;
        private int index;

        private MapBinder(Map<String, Object> parameters) {
            this.parameters = parameters;
        }

        @Override
        public Object autoPopulateRuntimeProperty(RuntimePersistentProperty<?> persistentProperty, @Nullable Object previousValue) {
            return runtimeEntityRegistry.autoPopulateRuntimeProperty(persistentProperty, previousValue);
        }

        @Override
        public @Nullable Object convert(@Nullable Object value, @Nullable RuntimePersistentProperty<?> property) {
            if (value == null || property == null) {
                return value;
            }
            if (property instanceof RuntimeAssociation<?> association && !association.isEmbedded()) {
                RuntimePersistentEntity<Object> associated = (RuntimePersistentEntity<Object>) association.getAssociatedEntity();
                if (associated.getIntrospection().getBeanType().isInstance(value)) {
                    return mapper.writeIdentity(associated, value);
                }
                return value;
            }
            return mapper.writeValue(property, value);
        }

        @Override
        public @Nullable Object convert(@Nullable Class<?> converterClass, @Nullable Object value, @Nullable Argument<?> argument) {
            if (converterClass == null) {
                return value;
            }
            AttributeConverter<Object, Object> converter = attributeConverterRegistry.getConverter(converterClass);
            return converter.convertToPersistedValue(value, argument == null ? ConversionContext.DEFAULT : ConversionContext.of(argument));
        }

        @Override
        public void bindOne(QueryParameterBinding binding, @Nullable Object value) {
            parameters.put(name(binding), mapper.encode(value));
            index++;
        }

        @Override
        public void bindMany(QueryParameterBinding binding, Collection<Object> values) {
            parameters.put(name(binding), mapper.encode(values));
            index++;
        }

        @Override
        public int currentIndex() {
            return index;
        }

        private String name(QueryParameterBinding binding) {
            String name = binding.getName();
            return name == null ? CypherQueryBuilder.PARAMETER_PREFIX + index : name;
        }
    }

    /**
     * Writes entities and their relationships within one session.
     */
    private final class Persister {
        private final GraphSession session;
        private final OperationContext context;
        private final Map<Object, Object> saved = new IdentityHashMap<>();

        private Persister(GraphSession session, AnnotationMetadata annotationMetadata, Class<?> repositoryType) {
            this.session = session;
            this.context = new OperationContext(annotationMetadata, repositoryType);
        }

        <T> T save(RuntimePersistentEntity<T> entity, T instance, boolean insert) {
            Object existing = saved.get(instance);
            if (existing != null) {
                return (T) existing;
            }
            saved.put(instance, instance);
            NodeOperation<T> operation = new NodeOperation<>(this, entity, instance, insert);
            if (insert) {
                operation.persist();
            } else {
                operation.update();
            }
            T result = operation.getEntity();
            saved.put(instance, result);
            return result;
        }

        <T> int delete(RuntimePersistentEntity<T> entity, T instance) {
            DeleteNodeOperation<T> operation = new DeleteNodeOperation<>(this, entity, instance);
            operation.delete();
            return operation.deleted;
        }

        GraphResult run(String query, Map<String, Object> parameters) {
            return session.run(query, parameters);
        }
    }

    /**
     * Writes one node and links its relationships.
     *
     * @param <T> The entity type
     */
    private final class NodeOperation<T> extends AbstractSyncEntityOperations<OperationContext, T, RuntimeException> {
        private final Persister persister;

        NodeOperation(Persister persister, RuntimePersistentEntity<T> entity, T instance, boolean insert) {
            super(persister.context, AbstractGraphRepositoryOperations.this.noCascade, entityEventRegistry, entity, AbstractGraphRepositoryOperations.this.conversionService, instance, insert);
            this.persister = persister;
        }

        @Override
        protected void cascadePre(Relation.Cascade cascadeType) {
            // Relationships are written after the node, see execute()
        }

        @Override
        protected void cascadePost(Relation.Cascade cascadeType) {
            // Relationships are written after the node, see execute()
        }

        @Override
        protected void execute() {
            RuntimePersistentProperty<T> identity = persistentEntity.getIdentity();
            Map<String, Object> properties = mapper.writeProperties(persistentEntity, entity);
            Object id = mapper.writeIdentity(persistentEntity, entity);
            if (identity != null && !GraphEntities.isNativeId(identity) && id == null) {
                throw new DataAccessException("Entity [" + persistentEntity.getSimpleName() + "] needs its identifier [" + identity.getName() + "] assigned before it is saved");
            }
            List<String> names = new ArrayList<>(properties.keySet());
            String key = "write:" + persistentEntity.getName() + ":" + (id != null) + ":" + names;
            String query = queryCache.computeIfAbsent(key, k -> queryBuilder.buildWriteNodeQuery(persistentEntity, names, id != null));
            Map<String, Object> parameters = new LinkedHashMap<>();
            int i = 0;
            for (Object value : properties.values()) {
                parameters.put(CypherQueryBuilder.VALUE_PARAMETER_PREFIX + i++, value);
            }
            if (id != null) {
                parameters.put(CypherQueryBuilder.ID_PARAMETER, id);
            }
            List<GraphRecord> records = persister.run(query, parameters).records();
            if (records.isEmpty()) {
                throw new DataAccessException("No node of [" + persistentEntity.getSimpleName() + "] with identifier [" + id + "] to update");
            }
            if (identity != null && GraphEntities.isNativeId(identity) && id == null) {
                Object generated = records.get(0).get(CypherQueryBuilder.ID_PARAMETER);
                Object generatedId = mapper.readValue(identity, generated);
                if (generatedId == null) {
                    throw new DataAccessException("The database returned no identifier for the new node of [" + persistentEntity.getSimpleName() + "]");
                }
                entity = updateEntityId((BeanProperty<T, Object>) identity.getProperty(), entity, generatedId);
            }
            List<String> labels = mapper.dynamicLabels(persistentEntity, entity);
            if (!labels.isEmpty()) {
                Object nodeId = mapper.writeIdentity(persistentEntity, entity);
                if (nodeId == null) {
                    throw new DataAccessException("Cannot add labels to [" + persistentEntity.getSimpleName() + "] without an identifier");
                }
                persister.run(queryBuilder.buildAddLabelsQuery(persistentEntity, labels), Map.of(CypherQueryBuilder.ID_PARAMETER, nodeId));
            }
            writeRelationships();
            writeDynamicRelationships();
        }

        private void writeRelationships() {
            for (RuntimePersistentProperty<T> property : persistentEntity.getPersistentProperties()) {
                if (!(property instanceof RuntimeAssociation<T> association) || !GraphEntities.isRelationship(association)) {
                    continue;
                }
                BeanProperty<T, Object> beanProperty = association.getProperty();
                Object value = beanProperty.get(entity);
                if (value == null) {
                    continue;
                }
                boolean many = value instanceof Iterable<?>;
                Iterable<Object> items = many ? (Iterable<Object>) value : List.of(value);
                List<Object> written = new ArrayList<>();
                boolean changed = false;
                for (Object item : items) {
                    Object result = link(association, item);
                    changed |= result != item;
                    written.add(result);
                }
                if (changed) {
                    Object newValue = many ? conversionService.convertRequired(written, beanProperty.asArgument()) : written.get(0);
                    entity = withProperty(beanProperty, entity, newValue);
                }
            }
        }

        private void writeDynamicRelationships() {
            BeanProperty<Object, Object> property = mapper.dynamicRelationshipsProperty(persistentEntity);
            if (property == null || !(property.get(entity) instanceof Map<?, ?> relationships) || relationships.isEmpty()) {
                return;
            }
            Object fromId = mapper.writeIdentity(persistentEntity, entity);
            Map<String, List<Object>> written = new LinkedHashMap<>();
            boolean changed = false;
            for (Map.Entry<?, ?> entry : relationships.entrySet()) {
                String type = String.valueOf(entry.getKey());
                Object value = entry.getValue();
                Iterable<?> targets = value instanceof Iterable<?> iterable ? iterable : value == null ? List.of() : List.of(value);
                List<Object> savedTargets = new ArrayList<>();
                for (Object target : targets) {
                    if (target == null) {
                        continue;
                    }
                    RuntimePersistentEntity<Object> targetEntity = (RuntimePersistentEntity<Object>) AbstractGraphRepositoryOperations.this.getEntity(target.getClass());
                    Object savedTarget = persister.save(targetEntity, target, isNew(targetEntity, target));
                    changed |= savedTarget != target;
                    savedTargets.add(savedTarget);
                    Object toId = mapper.writeIdentity(targetEntity, savedTarget);
                    if (fromId == null || toId == null) {
                        throw new DataAccessException("Cannot link a [" + type + "] relationship without the identifiers of both nodes");
                    }
                    String key = "dynamicLink:" + persistentEntity.getName() + ":" + targetEntity.getName() + ":" + type;
                    String query = queryCache.computeIfAbsent(key, k -> queryBuilder.buildDynamicLinkQuery(persistentEntity, targetEntity, type));
                    persister.run(query, Map.of(CypherQueryBuilder.LINK_FROM_PARAMETER, fromId, CypherQueryBuilder.LINK_TO_PARAMETER, toId));
                }
                written.put(type, savedTargets);
            }
            if (changed) {
                entity = withProperty((BeanProperty<T, Object>) (BeanProperty<?, ?>) property, entity, written);
            }
        }

        private Object link(RuntimeAssociation<T> association, Object item) {
            RuntimePersistentEntity<Object> associated = (RuntimePersistentEntity<Object>) association.getAssociatedEntity();
            Object fromId = mapper.writeIdentity(persistentEntity, entity);
            if (GraphEntities.isRelationshipEntity(associated)) {
                RuntimeAssociation<Object> targetAssociation = (RuntimeAssociation<Object>) targetNode(associated);
                BeanProperty<Object, Object> targetProperty = targetAssociation.getProperty();
                Object target = targetProperty.get(item);
                if (target == null) {
                    throw new DataAccessException("Relationship [" + associated.getSimpleName() + "] has no @TargetNode value");
                }
                RuntimePersistentEntity<Object> targetEntity = (RuntimePersistentEntity<Object>) targetAssociation.getAssociatedEntity();
                Object savedTarget = persister.save(targetEntity, target, isNew(targetEntity, target));
                if (savedTarget != target) {
                    item = withProperty(targetProperty, item, savedTarget);
                }
                Map<String, Object> properties = mapper.writeProperties(associated, item);
                RuntimePersistentProperty<Object> relationshipIdentity = associated.getIdentity();
                Object relationshipId = runLink(association, targetEntity, mapper.writeIdentity(targetEntity, savedTarget), fromId, properties, relationshipIdentity);
                if (relationshipIdentity != null && GraphEntities.isNativeId(relationshipIdentity) && relationshipId != null) {
                    BeanProperty<Object, Object> idProperty = relationshipIdentity.getProperty();
                    Object id = mapper.readValue(relationshipIdentity, relationshipId);
                    item = withProperty(idProperty, item, id);
                }
                return item;
            }
            Object savedTarget = persister.save(associated, item, isNew(associated, item));
            runLink(association, associated, mapper.writeIdentity(associated, savedTarget), fromId, Map.of(), null);
            return savedTarget;
        }

        private @Nullable Object runLink(RuntimeAssociation<T> association,
                                         RuntimePersistentEntity<?> target,
                                         @Nullable Object toId,
                                         @Nullable Object fromId,
                                         Map<String, Object> properties,
                                         @Nullable RuntimePersistentProperty<?> relationshipIdentity) {
            if (fromId == null || toId == null) {
                throw new DataAccessException("Cannot link [" + association.getName() + "] without the identifiers of both nodes");
            }
            List<String> names = new ArrayList<>(properties.keySet());
            String key = "link:" + persistentEntity.getName() + ":" + association.getName() + ":" + names;
            String query = queryCache.computeIfAbsent(key, k -> queryBuilder.buildLinkQuery(association, persistentEntity, target, names, relationshipIdentity));
            Map<String, Object> parameters = new LinkedHashMap<>();
            parameters.put(CypherQueryBuilder.LINK_FROM_PARAMETER, fromId);
            parameters.put(CypherQueryBuilder.LINK_TO_PARAMETER, toId);
            int i = 0;
            for (Object value : properties.values()) {
                parameters.put(CypherQueryBuilder.VALUE_PARAMETER_PREFIX + i++, value);
            }
            List<GraphRecord> records = persister.run(query, parameters).records();
            if (records.isEmpty()) {
                throw new DataAccessException("Could not link [" + association.getName() + "]: one of the nodes does not exist");
            }
            return records.get(0).get(CypherQueryBuilder.ID_PARAMETER);
        }
    }

    /**
     * Relationships are written by {@link NodeOperation} after the node, so the cascade operations are never used.
     */
    private static final class RelationshipsWrittenByNode implements SyncCascadeOperations.SyncCascadeOperationsHelper<OperationContext> {

        @Override
        public <T> T persistOne(OperationContext ctx, T entityValue, RuntimePersistentEntity<T> persistentEntity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> List<T> persistBatch(OperationContext ctx, Iterable<T> entityValues, RuntimePersistentEntity<T> persistentEntity, java.util.function.Predicate<T> predicate) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T updateOne(OperationContext ctx, T entityValue, RuntimePersistentEntity<T> persistentEntity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void persistManyAssociation(OperationContext ctx, RuntimeAssociation runtimeAssociation, Object parentEntityValue,
                                           RuntimePersistentEntity<Object> parentPersistentEntity, Object childEntityValue,
                                           RuntimePersistentEntity<Object> childPersistentEntity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void persistManyAssociationBatch(OperationContext ctx, RuntimeAssociation runtimeAssociation, Object parentEntityValue,
                                                RuntimePersistentEntity<Object> parentPersistentEntity, Iterable<Object> childEntityValues,
                                                RuntimePersistentEntity<Object> childPersistentEntity) {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Deletes one node with its relationships.
     *
     * @param <T> The entity type
     */
    private final class DeleteNodeOperation<T> extends AbstractSyncEntityOperations<OperationContext, T, RuntimeException> {
        private final Persister persister;
        private int deleted;

        DeleteNodeOperation(Persister persister, RuntimePersistentEntity<T> entity, T instance) {
            super(persister.context, AbstractGraphRepositoryOperations.this.noCascade, entityEventRegistry, entity, AbstractGraphRepositoryOperations.this.conversionService, instance, false);
            this.persister = persister;
        }

        @Override
        protected void cascadePre(Relation.Cascade cascadeType) {
            // Deleting a node detaches its relationships, there is nothing to cascade
        }

        @Override
        protected void cascadePost(Relation.Cascade cascadeType) {
            // Deleting a node detaches its relationships, there is nothing to cascade
        }

        @Override
        protected void execute() {
            Object id = mapper.writeIdentity(persistentEntity, entity);
            if (id == null) {
                throw new DataAccessException("Cannot delete [" + persistentEntity.getSimpleName() + "] without an identifier");
            }
            String query = queryCache.computeIfAbsent("delete:" + persistentEntity.getName(), k -> queryBuilder.buildDeleteByIdQuery(persistentEntity));
            deleted = updateCount(persister.run(query, Map.of(CypherQueryBuilder.ID_PARAMETER, id))).intValue();
        }
    }
}
