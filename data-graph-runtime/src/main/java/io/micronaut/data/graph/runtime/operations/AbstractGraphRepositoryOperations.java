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
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.data.annotation.Projection;
import io.micronaut.data.event.EntityEventListener;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.exceptions.EntityExistsException;
import io.micronaut.data.exceptions.NonUniqueResultException;
import io.micronaut.data.exceptions.OptimisticLockException;
import io.micronaut.data.graph.annotation.Direction;
import io.micronaut.data.graph.model.GraphEntities;
import io.micronaut.data.graph.model.GraphPath;
import io.micronaut.data.graph.model.GraphRelationship;
import io.micronaut.data.graph.model.query.PathDirectionParameter;
import io.micronaut.data.graph.model.query.PathLimitParameter;
import io.micronaut.data.graph.model.query.PathQueryDefinition;
import io.micronaut.data.graph.model.query.builder.CypherQueryBuilder;
import io.micronaut.data.graph.model.query.builder.GraphQueryBuilder;
import io.micronaut.data.graph.model.query.builder.LiteralMasker;
import io.micronaut.data.graph.runtime.GraphEdge;
import io.micronaut.data.graph.runtime.GraphEntityMapper;
import io.micronaut.data.graph.runtime.GraphNode;
import io.micronaut.data.graph.runtime.GraphPathValue;
import io.micronaut.data.graph.runtime.GraphRecord;
import io.micronaut.data.graph.runtime.GraphResult;
import io.micronaut.data.graph.runtime.GraphSession;
import io.micronaut.data.graph.runtime.GraphValueEncoderRegistrar;
import io.micronaut.data.graph.runtime.GraphValueEncoders;
import io.micronaut.data.graph.runtime.serde.GraphSerde;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.Limit;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
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
import io.micronaut.data.operations.async.AsyncRepositoryOperations;
import io.micronaut.data.operations.reactive.ReactiveCapableRepository;
import io.micronaut.data.operations.reactive.ReactiveRepositoryOperations;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.date.DateTimeProvider;
import io.micronaut.data.runtime.event.DefaultEntityEventContext;
import io.micronaut.data.runtime.operations.ExecutorAsyncOperations;
import io.micronaut.data.runtime.operations.ExecutorReactiveOperations;
import io.micronaut.data.runtime.operations.internal.AbstractRepositoryOperations;
import io.micronaut.data.runtime.operations.internal.AbstractSyncEntitiesOperations;
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

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private static final Pattern RETURN_CLAUSE = clause("RETURN");
    private static final Pattern ORDER_BY_CLAUSE = clause("ORDER\\s+BY");
    private static final Pattern SKIP_CLAUSE = clause("SKIP");
    private static final Pattern LIMIT_CLAUSE = clause("LIMIT");
    private static final Pattern CRITERIA_DEPTH = Pattern.compile(Pattern.quote(PathQueryDefinition.CRITERIA_DEPTH_TOKEN) + "(\\d+)");
    // Asked for every row of a relationship finder
    private static final ClassValue<Boolean> RELATIONSHIP_ENTITY = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return BeanIntrospector.SHARED.findIntrospection(type)
                .map(introspection -> introspection.hasStereotype(io.micronaut.data.graph.annotation.RelationshipProperties.class))
                .orElse(false);
        }
    };

    protected final GraphQueryBuilder queryBuilder;

    protected final GraphEntityMapper mapper;

    private final ExecutorServiceResolver executorServiceResolver;

    private final SynchronizedLazyValue<ExecutorAsyncOperations> asyncOperations = new SynchronizedLazyValue<>();

    /**
     * Generated queries by key. Writes of entities with schemaless properties generate a query per set
     * of property names, which callers control, so the cache evicts its least recently used entries.
     */
    private final Map<String, String> queryCache = Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > queryCacheSize;
        }
    });

    private volatile int queryCacheSize = GraphOperationsConfiguration.DEFAULT_QUERY_CACHE_SIZE;

    private volatile int maxPathDepth = GraphOperationsConfiguration.DEFAULT_MAX_PATH_DEPTH;

    private final GraphValueEncoders encoders = GraphValueEncoders.defaults();

    private final GraphSerde graphSerde;

    private final SyncCascadeOperations<OperationContext> noCascade;

    /**
     * @param queryBuilder               The query builder of the dialect
     * @param dateTimeProvider           The date time provider
     * @param runtimeEntityRegistry      The entity registry
     * @param conversionService          The conversion service
     * @param attributeConverterRegistry The attribute converter registry
     * @param executorService            The executor for asynchronous and reactive operations
     * @param encoderRegistrars          The registrars of the encoders of the application
     * @param graphSerde                 Reads entities and DTOs with Micronaut Serialization
     */
    protected AbstractGraphRepositoryOperations(GraphQueryBuilder queryBuilder,
                                                DateTimeProvider<Object> dateTimeProvider,
                                                RuntimeEntityRegistry runtimeEntityRegistry,
                                                DataConversionService conversionService,
                                                AttributeConverterRegistry attributeConverterRegistry,
                                                @Nullable ExecutorService executorService,
                                                List<GraphValueEncoderRegistrar> encoderRegistrars,
                                                GraphSerde graphSerde) {
        super(dateTimeProvider, runtimeEntityRegistry, conversionService, attributeConverterRegistry);
        this.queryBuilder = queryBuilder;
        registerEncoders(encoders);
        for (GraphValueEncoderRegistrar registrar : encoderRegistrars) {
            registrar.register(encoders);
        }
        this.graphSerde = graphSerde;
        this.mapper = new GraphEntityMapper(runtimeEntityRegistry, conversionService, this::encodeValue, graphSerde);
        this.executorServiceResolver = new ExecutorServiceResolver(executorService);
        this.noCascade = new SyncCascadeOperations<>(conversionService, new RelationshipsWrittenByNode());
    }

    /**
     * Applies the limits configured under {@code micronaut.data.graph}.
     *
     * @param configuration The configuration
     */
    @Inject
    public void setConfiguration(GraphOperationsConfiguration configuration) {
        this.maxPathDepth = configuration.getMaxPathDepth();
        this.queryCacheSize = configuration.getQueryCacheSize();
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
     * Runs work that writes several statements, which must all apply or none: within the current transaction, or
     * in a transaction of its own that commits when the work returns and rolls back when it throws. A database
     * module whose statements auto-commit outside a transaction overrides this method to begin one; the default
     * runs the work like {@link #write(AnnotationMetadata, Function)}.
     *
     * @param annotationMetadata The annotation metadata of the operation
     * @param work               The work
     * @param <R>                The result type
     * @return The result
     * @since 5.3.0
     */
    protected <R> R writeInTransaction(AnnotationMetadata annotationMetadata, Function<GraphSession, R> work) {
        return write(annotationMetadata, work);
    }

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
    public AsyncRepositoryOperations async() {
        return executorAsync();
    }

    private ExecutorAsyncOperations executorAsync() {
        return asyncOperations.get(() -> new ExecutorAsyncOperations(this, executorServiceResolver.get()));
    }

    /**
     * The executor that runs asynchronous operations.
     *
     * @return The executor
     */
    protected Executor executor() {
        return executorServiceResolver.get();
    }

    @Override
    public ReactiveRepositoryOperations reactive() {
        return new ExecutorReactiveOperations(executorAsync(), conversionService);
    }

    @Override
    public void close() {
        executorServiceResolver.close();
    }

    // Queries

    @Override
    public @Nullable <T> T findOne(Class<T> type, Object id) {
        RuntimePersistentEntity<T> entity = getEntity(type);
        String query = findByIdQuery(entity);
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
            if (records.size() > 1 && !preparedQuery.getQueryLimit().isLimited()) {
                throw new NonUniqueResultException();
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
        checkOffsetPageable(query.getPageable());
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

    static Number updateCount(GraphResult result) {
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

    final Map<String, Object> encodeParameters(Map<String, Object> parameters) {
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
        StringBuilder query = new StringBuilder(checkCriteriaDepths(preparedQuery.getQuery()));
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
            if (depth > maxPathDepth) {
                // A variable-length path visits every path up to its depth, so a caller-supplied depth is capped
                throw new IllegalArgumentException("The @PathLimit argument " + depth + " is larger than "
                    + GraphOperationsConfiguration.PREFIX + ".max-path-depth (" + maxPathDepth + ")");
            }
            // A query that matches its path twice has the token twice
            while (tokenPosition >= 0) {
                query.replace(tokenPosition, tokenPosition + PathQueryDefinition.PATH_LIMIT_TOKEN.length(), String.valueOf(depth));
                tokenPosition = query.indexOf(PathQueryDefinition.PATH_LIMIT_TOKEN, tokenPosition);
            }
        }
        applyDirection(preparedQuery, query);
        if (applyLimits && !preparedQuery.isCount()) {
            checkOffsetPageable(preparedQuery.getPageable());
            RuntimePersistentEntity<?> entity = getEntity(preparedQuery.getRootEntity());
            appendSortAndLimit(query, entity, preparedQuery.getSort(), unrenderedLimit(preparedQuery, query));
        }
        return new GraphQuery(query.toString(), parameters);
    }

    private String checkCriteriaDepths(String query) {
        if (!query.contains(PathQueryDefinition.CRITERIA_DEPTH_TOKEN)) {
            return query;
        }
        return CRITERIA_DEPTH.matcher(query).replaceAll(match -> {
            // A criterion takes its depth at runtime, possibly from a request, so it is capped like a @PathLimit argument
            int depth = Integer.parseInt(match.group(1));
            if (depth > maxPathDepth) {
                throw new IllegalArgumentException("The depth " + depth + " of a graph criterion is larger than "
                    + GraphOperationsConfiguration.PREFIX + ".max-path-depth (" + maxPathDepth + ")");
            }
            return match.group(1);
        });
    }

    /**
     * The limit left to append. The criteria runtime renders the {@code Limit} argument of a method into the query it
     * builds and still reports it on the prepared query.
     */
    private Limit unrenderedLimit(PreparedQuery<?, ?> preparedQuery, StringBuilder query) {
        Limit limit = preparedQuery.getQueryLimit();
        if (limit.isLimited() && !preparedQuery.isRawQuery()
            && LiteralMasker.mask(query.toString()).contains(limitClause(query, limit))) {
            return Limit.UNLIMITED;
        }
        return limit;
    }

    /**
     * The text of a limit in a query.
     *
     * @param query The query
     * @param limit The limit
     * @return The text the criteria runtime renders for the limit
     */
    protected String limitClause(StringBuilder query, Limit limit) {
        return queryBuilder.buildLimitAndOffset(limit.maxResults(), limit.offset());
    }

    /**
     * Rejects a cursored pageable: without keyset pagination an offset page would hand out cursors that do not work.
     *
     * @param pageable The pageable
     */
    static void checkOffsetPageable(Pageable pageable) {
        if (pageable.getMode() != Pageable.Mode.OFFSET) {
            throw new UnsupportedOperationException("Graph repositories do not support cursored pagination: return a Page and pass an offset Pageable instead of a CursoredPage or CursoredPageable");
        }
    }

    /**
     * Renders the {@link io.micronaut.data.graph.annotation.PathDirection} argument of a path or relationship finder
     * in place of the direction tokens of its query.
     *
     * @param preparedQuery The prepared query
     * @param query         The query
     */
    private static void applyDirection(PreparedQuery<?, ?> preparedQuery, StringBuilder query) {
        int left = query.indexOf(PathQueryDefinition.PATH_LEFT_TOKEN);
        if (left < 0) {
            return;
        }
        AnnotationValue<PathDirectionParameter> parameter = preparedQuery.getAnnotationMetadata().getAnnotation(PathDirectionParameter.class);
        if (parameter == null) {
            throw new DataAccessException("Path query has no @PathDirection argument: " + query);
        }
        Object[] parameters = preparedQuery.getParameterArray();
        int index = parameter.intValue().orElse(-1);
        if (index < 0 || index >= parameters.length) {
            throw new DataAccessException("Path query has no @PathDirection argument at index " + index + ": " + query);
        }
        Object argument = parameters[index];
        if (!(argument instanceof Direction direction)) {
            throw new DataAccessException("The @PathDirection argument must be a Direction, was: " + argument);
        }
        if (direction == Direction.UNDIRECTED && parameter.booleanValue("outgoingIfUndirected").orElse(false)) {
            direction = Direction.OUTGOING;
        }
        String leftArrow = direction == Direction.INCOMING ? "<-" : "-";
        String rightArrow = direction == Direction.OUTGOING ? "->" : "-";
        while (left >= 0) {
            query.replace(left, left + PathQueryDefinition.PATH_LEFT_TOKEN.length(), leftArrow);
            left = query.indexOf(PathQueryDefinition.PATH_LEFT_TOKEN);
        }
        int right = query.indexOf(PathQueryDefinition.PATH_RIGHT_TOKEN);
        while (right >= 0) {
            query.replace(right, right + PathQueryDefinition.PATH_RIGHT_TOKEN.length(), rightArrow);
            right = query.indexOf(PathQueryDefinition.PATH_RIGHT_TOKEN);
        }
    }

    /**
     * Appends the ordering and limits requested by method arguments to a query. A query that cannot
     * take them, because it is an explicit query or already sorts or limits itself, is rejected
     * rather than silently returned unsorted or unpaged.
     *
     * @param query  The query
     * @param entity The root entity
     * @param sort   The sort
     * @param limit  The limit
     */
    protected void appendSortAndLimit(StringBuilder query, RuntimePersistentEntity<?> entity, Sort sort, Limit limit) {
        if (sort != null && sort.isSorted()) {
            appendSort(query, entity, sort);
        }
        if (limit != null && limit.isLimited()) {
            String masked = maskedWithoutTrailingComment(query);
            // A WITH clause may limit what it carries, as the shortest of the paths up to a depth; the final RETURN may not
            int returnIndex = lastClause(masked, RETURN_CLAUSE, -1);
            if (lastClause(masked, LIMIT_CLAUSE, returnIndex) >= 0 || lastClause(masked, SKIP_CLAUSE, returnIndex) >= 0) {
                throw new DataAccessException("Cannot page the query [" + query + "]: it declares SKIP or LIMIT itself");
            }
            query.append(queryBuilder.buildLimitAndOffset(limit.maxResults(), limit.offset()));
        }
    }

    private void appendSort(StringBuilder query, RuntimePersistentEntity<?> entity, Sort sort) {
        boolean rootQuery = query.indexOf("MATCH (" + CypherQueryBuilder.ROOT) == 0;
        if (!rootQuery) {
            throw new DataAccessException("Cannot apply the sort " + sort.getOrderBy() + " to the query [" + query + "]: it does not match the root node as " + CypherQueryBuilder.ROOT);
        }
        // The ordering the query declares takes precedence, and SKIP and LIMIT stay last
        String masked = maskedWithoutTrailingComment(query);
        int returnIndex = lastClause(masked, RETURN_CLAUSE, -1);
        int tailIndex = lastClause(masked, SKIP_CLAUSE, returnIndex);
        int limitIndex = lastClause(masked, LIMIT_CLAUSE, returnIndex);
        if (tailIndex < 0 || (limitIndex >= 0 && limitIndex < tailIndex)) {
            tailIndex = limitIndex;
        }
        String tail = tailIndex < 0 ? "" : " " + query.substring(tailIndex);
        if (tailIndex >= 0) {
            query.setLength(query.substring(0, tailIndex).stripTrailing().length());
        }
        query.append(lastClause(masked, ORDER_BY_CLAUSE, returnIndex) >= 0 ? ", " : " ORDER BY ");
        appendOrderItems(query, entity, sort);
        query.append(tail);
    }

    /**
     * @return A clause keyword in any case, which a property, parameter or alias of the same name is not
     */
    private static Pattern clause(String keyword) {
        return Pattern.compile("(?<![.$\\w])(?<!\\bAS\\s{1,8})" + keyword + "(?!\\w)", Pattern.CASE_INSENSITIVE);
    }

    /**
     * Drops a comment that ends the query, which would swallow anything appended to it.
     *
     * @return The query with its literals and comments masked, to look for clauses in
     */
    private static String maskedWithoutTrailingComment(StringBuilder query) {
        String masked = LiteralMasker.mask(query.toString()).stripTrailing();
        query.setLength(masked.length());
        return masked;
    }

    /**
     * @return The index of the last occurrence of a clause after the given index, or -1
     */
    private static int lastClause(String masked, Pattern clause, int after) {
        Matcher matcher = clause.matcher(masked);
        int index = -1;
        while (matcher.find()) {
            index = matcher.start();
        }
        return index > after ? index : -1;
    }

    private void appendOrderItems(StringBuilder query, RuntimePersistentEntity<?> entity, Sort sort) {
        Iterator<Sort.Order> iterator = sort.getOrderBy().iterator();
        while (iterator.hasNext()) {
            Sort.Order order = iterator.next();
            RuntimePersistentProperty<?> property = sortProperty(entity, order.getProperty());
            String expression = property == entity.getIdentity() && GraphEntities.isNativeId(property)
                ? queryBuilder.rootIdentityExpression(property)
                : CypherQueryBuilder.ROOT + "." + GraphEntities.escapeName(property.getPersistedName());
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

    final <R> @Nullable R mapRecord(PreparedQuery<?, R> preparedQuery, GraphRecord record) {
        Object first = record.size() == 0 ? null : record.get(0);
        Argument<R> resultArgument = preparedQuery.getResultArgument();
        Class<R> resultType = preparedQuery.getResultType();
        if (resultType == Object[].class) {
            // Object[] rows are the values of the columns, in column order
            return (R) record.values().toArray();
        }
        if (preparedQuery.isDtoProjection()) {
            Map<String, Object> values = record.size() == 1 && first instanceof GraphNode node ? node.properties() : record.asMap();
            return readDto(projected(values, resultType, preparedQuery.getAnnotationMetadata()), resultType);
        }
        Class<?> nodeType = nodeType(resultArgument, preparedQuery.getRootEntity());
        return (R) mapValue(record, first, resultType, nodeType, targetNodeType(resultArgument, nodeType), preparedQuery.getAnnotationMetadata());
    }

    final @Nullable Object mapValue(GraphRecord record, @Nullable Object first, Class<?> resultType, Class<?> nodeType, AnnotationMetadata annotationMetadata) {
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
    final @Nullable Object mapValue(GraphRecord record, @Nullable Object first, Class<?> resultType, Class<?> nodeType,
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
        if (first instanceof GraphEdge edge && isRelationshipEntity(resultType)) {
            // A relationship finder returning the relationship entity, whose target node is the far end of the pattern
            GraphNode to = record.get(CypherQueryBuilder.RELATIONSHIP_TO) instanceof GraphNode node ? node : null;
            return readRelationshipEntity(getEntity(resultType), edge, to, annotationMetadata);
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

    final <T> T readEntity(RuntimePersistentEntity<T> entity, GraphRecord record, AnnotationMetadata annotationMetadata) {
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

    private <T> T readRelationshipEntity(RuntimePersistentEntity<T> entity, GraphEdge edge, @Nullable GraphNode to, AnnotationMetadata annotationMetadata) {
        return triggerPostLoad(mapper.readRelationshipEntity(entity, edge, to), entity, annotationMetadata);
    }

    private static boolean isRelationshipEntity(Class<?> type) {
        return RELATIONSHIP_ENTITY.get(type);
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
        return graphSerde.read(type, values);
    }

    /**
     * Adds the columns a DTO selects under another name with {@link Projection} under the names of its properties.
     * The projection is declared on the properties or, in order, on the method: the processor rejects a method and DTO that both declare it.
     *
     * @param values             The columns of the row
     * @param type               The DTO type
     * @param annotationMetadata The annotation metadata of the method
     * @return The columns, under the names of the DTO properties
     */
    private static Map<String, Object> projected(Map<String, Object> values, Class<?> type, AnnotationMetadata annotationMetadata) {
        Optional<BeanIntrospection<Object>> introspection = BeanIntrospector.SHARED.findIntrospection((Class<Object>) type);
        if (introspection.isEmpty()) {
            return values;
        }
        Iterator<String> methodProjections = annotationMetadata.getAnnotationValuesByType(Projection.class).stream()
            .flatMap(projection -> projection.stringValue().stream())
            .iterator();
        Map<String, Object> projected = null;
        for (BeanProperty<Object, Object> property : introspection.get().getBeanProperties()) {
            String projection = property.stringValue(Projection.class).orElse(null);
            if (projection == null && methodProjections.hasNext()) {
                projection = methodProjections.next();
            }
            if (projection != null && !projection.equals(property.getName()) && values.containsKey(projection) && !values.containsKey(property.getName())) {
                if (projected == null) {
                    projected = new LinkedHashMap<>(values);
                }
                projected.put(property.getName(), values.get(projection));
            }
        }
        return projected == null ? values : projected;
    }

    // Writes

    @Override
    public <T> T persist(InsertOperation<T> operation) {
        return writeInTransaction(operation.getAnnotationMetadata(), session -> new Persister(session, operation.getAnnotationMetadata(), operation.getRepositoryType())
            .save(getEntity(operation.getRootEntity()), operation.getEntity(), true));
    }

    @Override
    public <T> Iterable<T> persistAll(InsertBatchOperation<T> operation) {
        return writeInTransaction(operation.getAnnotationMetadata(), session -> {
            Persister persister = new Persister(session, operation.getAnnotationMetadata(), operation.getRepositoryType());
            return persister.saveAll(getEntity(operation.getRootEntity()), operation, true);
        });
    }

    @Override
    public <T> T update(UpdateOperation<T> operation) {
        return writeInTransaction(operation.getAnnotationMetadata(), session -> new Persister(session, operation.getAnnotationMetadata(), operation.getRepositoryType())
            .save(getEntity(operation.getRootEntity()), operation.getEntity(), false));
    }

    @Override
    public <T> Iterable<T> updateAll(UpdateBatchOperation<T> operation) {
        return writeInTransaction(operation.getAnnotationMetadata(), session -> {
            Persister persister = new Persister(session, operation.getAnnotationMetadata(), operation.getRepositoryType());
            return persister.saveAll(getEntity(operation.getRootEntity()), operation, false);
        });
    }

    @Override
    public <T> int delete(DeleteOperation<T> operation) {
        return writeInTransaction(operation.getAnnotationMetadata(), session -> new Persister(session, operation.getAnnotationMetadata(), operation.getRepositoryType())
            .delete(getEntity(operation.getRootEntity()), operation.getEntity()));
    }

    @Override
    public <T> Optional<Number> deleteAll(DeleteBatchOperation<T> operation) {
        RuntimePersistentEntity<T> entity = getEntity(operation.getRootEntity());
        if (operation.all()) {
            String query = deleteAllQuery(entity);
            return Optional.of(write(operation.getAnnotationMetadata(), session -> updateCount(session.run(query, Map.of()))));
        }
        return Optional.of(writeInTransaction(operation.getAnnotationMetadata(), session -> {
            Persister persister = new Persister(session, operation.getAnnotationMetadata(), operation.getRepositoryType());
            int count = 0;
            for (T instance : operation) {
                count += persister.delete(entity, instance);
            }
            return count;
        }));
    }

    static <B> B withProperty(BeanProperty<B, Object> property, B bean, @Nullable Object value) {
        if (property.isReadOnly()) {
            return property.withValue(bean, value);
        }
        property.set(bean, value);
        return bean;
    }

    /**
     * @return The listener that triggers entity events
     */
    final EntityEventListener<Object> entityEventListener() {
        return entityEventRegistry;
    }

    /**
     * @param entity The root entity
     * @return The query that finds a node by its identifier
     */
    final String findByIdQuery(RuntimePersistentEntity<?> entity) {
        return queryCache.computeIfAbsent("findById:" + entity.getName(), k -> queryBuilder.buildFindByIdQuery(entity));
    }

    /**
     * @param entity The root entity
     * @return The query that deletes all nodes of the entity, returning their count
     */
    final String deleteAllQuery(RuntimePersistentEntity<?> entity) {
        return queryCache.computeIfAbsent("deleteAll:" + entity.getName(), k -> queryBuilder.buildDeleteAllQuery(entity));
    }

    /**
     * Checks that no schemaless property of the instances has the name of a declared property, for writes that
     * store the schemaless properties in a column of their own, which reads would hide behind the declared column.
     *
     * @param type      The entity type
     * @param instances The instances to write
     * @param <T>       The entity type
     */
    protected final <T> void checkDynamicAttributes(Class<T> type, Iterable<T> instances) {
        RuntimePersistentEntity<T> entity = mapper.getEntity(type);
        for (T instance : instances) {
            mapper.checkDynamicAttributes(entity, instance);
        }
    }

    /**
     * @param query A query in the database's query language, passed to the operations at runtime
     * @return The statement that runs it
     */
    final String finishedQuery(String query) {
        return queryCache.computeIfAbsent("query:" + query, k -> queryBuilder.finishQuery(query));
    }

    /**
     * Plans the query that creates or updates a node.
     *
     * @param entity   The entity
     * @param instance The instance
     * @param <T>      The entity type
     * @return The query, with the identifier written, or null when the database generates it
     */
    final <T> NodeWrite planNodeWrite(RuntimePersistentEntity<T> entity, T instance) {
        return planNodeWrite(entity, instance, false, null);
    }

    /**
     * Plans the query that creates or updates a node. An insert of an entity with a {@code @Version} property and an
     * assigned identifier writes nothing when a node with the identifier exists, rather than overwrite it. An update
     * of an entity with a {@code @Version} property only matches the node while it still has the version the entity
     * was read with.
     *
     * @param entity          The entity
     * @param instance        The instance
     * @param insert          Whether the node is inserted
     * @param previousVersion The version the entity was read with, or null to write without checking it
     * @param <T>             The entity type
     * @return The query, with the identifier written, or null when the database generates it
     */
    final <T> NodeWrite planNodeWrite(RuntimePersistentEntity<T> entity, T instance, boolean insert, @Nullable Object previousVersion) {
        RuntimePersistentProperty<T> identity = entity.getIdentity();
        Map<String, Object> properties = mapper.writeProperties(entity, instance);
        Object id = mapper.writeIdentity(entity, instance);
        if (identity != null && !GraphEntities.isNativeId(identity) && id == null) {
            throw new DataAccessException("Entity [" + entity.getSimpleName() + "] needs its identifier [" + identity.getName() + "] assigned before it is saved");
        }
        // Checked ahead of the write, so that a label that cannot be stored leaves the node as it was
        String addLabels = addLabelsQuery(entity, instance);
        List<String> names = new ArrayList<>(properties.keySet());
        RuntimePersistentProperty<T> version = entity.hasVersion() ? entity.getVersion() : null;
        boolean versioned = id != null && version != null && previousVersion != null;
        // Saving merges a node on its assigned identifier, but a new versioned entity must not overwrite the node and
        // its version
        boolean assignedInsert = insert && version != null && id != null && identity != null && !GraphEntities.isNativeId(identity);
        String query = nodeWriteQuery(entity, names, id != null, versioned, assignedInsert);
        Map<String, Object> parameters = new LinkedHashMap<>();
        int i = 0;
        for (Object value : properties.values()) {
            parameters.put(CypherQueryBuilder.VALUE_PARAMETER_PREFIX + i++, value);
        }
        if (id != null) {
            parameters.put(CypherQueryBuilder.ID_PARAMETER, id);
        }
        if (versioned && version != null) {
            parameters.put(CypherQueryBuilder.VERSION_PARAMETER, mapper.writeValue(version, previousVersion));
        }
        return new NodeWrite(new GraphQuery(query, parameters), id, versioned ? previousVersion : null, assignedInsert, addLabels);
    }

    private String nodeWriteQuery(RuntimePersistentEntity<?> entity, List<String> names, boolean idPresent, boolean versioned, boolean assignedInsert) {
        String kind = versioned ? "writeVersioned:" : assignedInsert ? "insert:" : "write:";
        String key = kind + entity.getName() + ":" + idPresent + ":" + names;
        return queryCache.computeIfAbsent(key, k -> {
            if (versioned) {
                return queryBuilder.buildUpdateVersionedNodeQuery(entity, names);
            }
            return assignedInsert ? queryBuilder.buildInsertNodeQuery(entity, names) : queryBuilder.buildWriteNodeQuery(entity, names, idPresent);
        });
    }

    /**
     * Builds the statements that create the indexes and unique constraints of entities, and of the node entities their
     * relationships reach, when they do not exist yet.
     *
     * @param entityTypes The entity types
     * @return The statements
     * @since 5.3.0
     */
    protected final List<String> buildCreateIndexStatements(Collection<Class<?>> entityTypes) {
        Map<String, PersistentEntity> entities = new LinkedHashMap<>();
        for (Class<?> entityType : entityTypes) {
            collectReachableNodes(getEntity(entityType), entities);
        }
        List<String> statements = new ArrayList<>();
        for (PersistentEntity entity : entities.values()) {
            statements.addAll(queryBuilder.buildCreateIndexStatements(entity));
        }
        return statements;
    }

    private void collectReachableNodes(PersistentEntity entity, Map<String, PersistentEntity> entities) {
        if (entities.putIfAbsent(entity.getName(), entity) != null) {
            return;
        }
        for (PersistentProperty property : entity.getPersistentProperties()) {
            if (property instanceof Association association && GraphEntities.isRelationship(association)) {
                collectReachableNodes(GraphEntities.targetNodeEntity(association), entities);
            }
        }
    }

    /**
     * Reads the {@code @Version} property of an instance about to be updated or deleted.
     *
     * @param entity   The entity
     * @param instance The instance
     * @param <T>      The entity type
     * @return The version, or null if the entity has no version
     */
    final <T> @Nullable Object currentVersion(RuntimePersistentEntity<T> entity, T instance) {
        RuntimePersistentProperty<T> version = entity.hasVersion() ? entity.getVersion() : null;
        return version == null ? null : version.getProperty().get(instance);
    }

    /**
     * Fails a versioned delete that deleted nothing, because the node was changed or deleted since it was read.
     *
     * @param entity          The entity
     * @param previousVersion The version the entity was read with, or null if the delete was not versioned
     * @param deleted         The number of deleted nodes
     */
    final void checkVersionedDelete(RuntimePersistentEntity<?> entity, @Nullable Object previousVersion, long deleted) {
        if (previousVersion != null && entity.hasVersion() && deleted == 0) {
            throw new OptimisticLockException("The node of [" + entity.getSimpleName() + "] was changed or deleted since it was read with version [" + previousVersion + "]");
        }
    }

    /**
     * Plans which batched write a node joins: nodes are created, updated or updated with a version check together,
     * and new versioned nodes with assigned identifiers are inserted without overwriting existing nodes, see
     * {@link #planNodeWrite(RuntimePersistentEntity, Object, boolean, Object)}.
     *
     * @param entity          The entity
     * @param instance        The instance
     * @param insert          Whether the node is inserted
     * @param previousVersion The version the instance was read with, or null
     * @param <T>             The entity type
     * @return The batch
     */
    final <T> NodeBatch planNodeBatch(RuntimePersistentEntity<T> entity, T instance, boolean insert, @Nullable Object previousVersion) {
        RuntimePersistentProperty<T> identity = entity.getIdentity();
        Object id = mapper.writeIdentity(entity, instance);
        if (identity != null && !GraphEntities.isNativeId(identity) && id == null) {
            throw new DataAccessException("Entity [" + entity.getSimpleName() + "] needs its identifier [" + identity.getName() + "] assigned before it is saved");
        }
        List<String> names = List.copyOf(mapper.writeProperties(entity, instance).keySet());
        boolean versioned = id != null && entity.hasVersion() && previousVersion != null;
        boolean assignedInsert = insert && entity.hasVersion() && id != null && identity != null && !GraphEntities.isNativeId(identity);
        // Nodes with assigned identifiers are merged, or inserted when new and versioned, which some databases cannot
        // do for more than one node of an UNWIND
        boolean merge = identity != null && !GraphEntities.isNativeId(identity) && !versioned;
        return new NodeBatch(names, id != null, versioned, assignedInsert, !merge || queryBuilder.supportsBatchedMerge());
    }

    /**
     * The row of a node in a batched write: its index in the batch, identifier, version and properties.
     *
     * @param entity          The entity
     * @param instance        The instance
     * @param index           The index of the node in the batch
     * @param previousVersion The version the node must still have, or null
     * @param <T>             The entity type
     * @return The row
     */
    final <T> Map<String, @Nullable Object> nodeRow(RuntimePersistentEntity<T> entity, T instance, int index, @Nullable Object previousVersion) {
        Map<String, @Nullable Object> row = new LinkedHashMap<>();
        row.put(CypherQueryBuilder.ROW_POSITION, index);
        row.put(CypherQueryBuilder.ID_PARAMETER, mapper.writeIdentity(entity, instance));
        if (previousVersion != null && entity.hasVersion()) {
            row.put(CypherQueryBuilder.VERSION_PARAMETER, mapper.writeValue(entity.getVersion(), previousVersion));
        }
        row.put(CypherQueryBuilder.ROW_PROPERTIES, mapper.writeProperties(entity, instance));
        return row;
    }

    /**
     * Fails a batched write that wrote fewer nodes than it was given. A versioned update or an insert writes all
     * nodes or none.
     *
     * @param entity  The entity
     * @param batch   The batch
     * @param rows    The rows of the nodes in the batch
     * @param written The number of nodes the query returned
     */
    final void checkBatchWritten(RuntimePersistentEntity<?> entity, NodeBatch batch, List<Map<String, @Nullable Object>> rows, int written) {
        int expected = rows.size();
        if (written == expected) {
            return;
        }
        if (batch.versioned()) {
            throw new OptimisticLockException("Expected to update " + expected + " nodes of [" + entity.getSimpleName()
                + "] but not all of them still had the version they were read with, so none was updated");
        }
        if (batch.insert()) {
            throw new EntityExistsException("A node of [" + entity.getSimpleName() + "] with one of the identifiers "
                + rows.stream().map(row -> row.get(CypherQueryBuilder.ID_PARAMETER)).toList() + " already exists, so none was inserted");
        }
        throw new DataAccessException("Expected to write " + expected + " nodes of [" + entity.getSimpleName() + "] but wrote " + written);
    }

    /**
     * Reads the identifier the database generated for a node of a batched write.
     *
     * @param entity The entity
     * @param record The row of the node
     * @return The identifier
     */
    final Object batchGeneratedId(RuntimePersistentEntity<?> entity, GraphRecord record) {
        Object generatedId = mapper.readValue(entity.getIdentity(), record.get(CypherQueryBuilder.ID_PARAMETER));
        if (generatedId == null) {
            throw new DataAccessException("The database returned no identifier for a new node of [" + entity.getSimpleName() + "]");
        }
        return generatedId;
    }

    /**
     * @param entity The entity
     * @param batch  The batch
     * @return The query that writes the nodes of a batch
     */
    final String batchQuery(RuntimePersistentEntity<?> entity, NodeBatch batch) {
        return queryCache.computeIfAbsent("writeNodes:" + entity.getName() + ":" + batch, k -> batch.insert()
            ? queryBuilder.buildInsertNodesQuery(entity, batch.propertyNames())
            : queryBuilder.buildWriteNodesQuery(entity, batch.propertyNames(), batch.idPresent(), batch.versioned()));
    }

    /**
     * Reads the identifier the database generated for a new node from the result of its write query.
     *
     * @param entity  The entity
     * @param write   The write
     * @param records The rows the write returned
     * @return The generated identifier, or null when the identifier was assigned
     */
    final @Nullable Object generatedId(RuntimePersistentEntity<?> entity, NodeWrite write, List<GraphRecord> records) {
        if (records.isEmpty()) {
            if (write.previousVersion() != null) {
                throw new OptimisticLockException("The node of [" + entity.getSimpleName() + "] with identifier [" + write.id()
                    + "] was changed or deleted since it was read with version [" + write.previousVersion() + "]");
            }
            if (write.insert()) {
                throw new EntityExistsException("A node of [" + entity.getSimpleName() + "] with identifier [" + write.id() + "] already exists");
            }
            throw new DataAccessException("No node of [" + entity.getSimpleName() + "] with identifier [" + write.id() + "] to update");
        }
        RuntimePersistentProperty<?> identity = entity.getIdentity();
        if (identity == null || !GraphEntities.isNativeId(identity) || write.id() != null) {
            return null;
        }
        Object generatedId = mapper.readValue(identity, records.get(0).get(CypherQueryBuilder.ID_PARAMETER));
        if (generatedId == null) {
            throw new DataAccessException("The database returned no identifier for the new node of [" + entity.getSimpleName() + "]");
        }
        return generatedId;
    }

    /**
     * Builds the query that adds the dynamic labels of an instance, which rejects a label that cannot be stored.
     *
     * @param entity   The entity
     * @param instance The instance
     * @param <T>      The entity type
     * @return The query, or null if the instance has no dynamic labels
     */
    final <T> @Nullable String addLabelsQuery(RuntimePersistentEntity<T> entity, T instance) {
        List<String> labels = mapper.dynamicLabels(entity, instance);
        return labels.isEmpty() ? null : queryBuilder.buildAddLabelsQuery(entity, labels);
    }

    /**
     * Plans the query that adds the dynamic labels of an instance to its node.
     *
     * @param entity    The entity
     * @param addLabels The query that adds the labels, see {@link #addLabelsQuery}, or null
     * @param instance  The saved instance
     * @param <T>       The entity type
     * @return The query, or null if the instance has no dynamic labels
     */
    final <T> @Nullable GraphQuery planAddLabels(RuntimePersistentEntity<T> entity, @Nullable String addLabels, T instance) {
        if (addLabels == null) {
            return null;
        }
        Object nodeId = mapper.writeIdentity(entity, instance);
        if (nodeId == null) {
            throw new DataAccessException("Cannot add labels to [" + entity.getSimpleName() + "] without an identifier");
        }
        return new GraphQuery(addLabels, Map.of(CypherQueryBuilder.ID_PARAMETER, nodeId));
    }

    /**
     * Plans the query that links an owner to a target node with the relationship of an association.
     *
     * @param association          The association
     * @param owner                The owning entity
     * @param target               The target entity
     * @param fromId               The identifier of the owner
     * @param toId                 The identifier of the target
     * @param properties           The relationship properties
     * @param relationshipIdentity The identity of the relationship entity, or null
     * @param mode                 How the relationship is written
     * @param relationshipId       The identifier of the relationship to update, or null
     * @return The query
     */
    final GraphQuery planLink(RuntimeAssociation<?> association,
                        RuntimePersistentEntity<?> owner,
                        RuntimePersistentEntity<?> target,
                        @Nullable Object fromId,
                        @Nullable Object toId,
                        Map<String, Object> properties,
                        @Nullable RuntimePersistentProperty<?> relationshipIdentity,
                        RelationshipTarget.LinkMode mode,
                        @Nullable Object relationshipId) {
        if (fromId == null || toId == null) {
            throw new DataAccessException("Cannot link [" + association.getName() + "] without the identifiers of both nodes");
        }
        List<String> names = new ArrayList<>(properties.keySet());
        String key = "link:" + mode + ":" + owner.getName() + ":" + association.getName() + ":" + names;
        String query = queryCache.computeIfAbsent(key, k -> switch (mode) {
            case MERGE -> queryBuilder.buildLinkQuery(association, owner, target, names, relationshipIdentity);
            case CREATE -> queryBuilder.buildCreateLinkQuery(association, owner, target, names, Objects.requireNonNull(relationshipIdentity));
            case UPDATE -> queryBuilder.buildUpdateLinkQuery(association, owner, target, names, Objects.requireNonNull(relationshipIdentity));
        });
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put(CypherQueryBuilder.LINK_FROM_PARAMETER, fromId);
        parameters.put(CypherQueryBuilder.LINK_TO_PARAMETER, toId);
        if (relationshipId != null) {
            parameters.put(CypherQueryBuilder.ID_PARAMETER, relationshipId);
        }
        int i = 0;
        for (Object value : properties.values()) {
            parameters.put(CypherQueryBuilder.VALUE_PARAMETER_PREFIX + i++, value);
        }
        return new GraphQuery(query, parameters);
    }

    /**
     * Plans the query that links an owner to many target nodes with relationships that have no identifier, one row
     * per relationship. The rows write the same relationship properties.
     *
     * @param association   The association
     * @param owner         The owning entity
     * @param target        The target entity
     * @param rows          The rows
     * @param propertyNames The stored names of the relationship properties every row writes
     * @return The query
     */
    final GraphQuery planLinks(RuntimeAssociation<?> association,
                               RuntimePersistentEntity<?> owner,
                               RuntimePersistentEntity<?> target,
                               List<RelationshipTarget.LinkRow> rows,
                               List<String> propertyNames) {
        String key = "links:" + owner.getName() + ":" + association.getName() + ":" + propertyNames;
        String query = queryCache.computeIfAbsent(key, k -> queryBuilder.buildLinksQuery(association, owner, target, propertyNames, null));
        List<Map<String, Object>> parameters = new ArrayList<>(rows.size());
        for (RelationshipTarget.LinkRow row : rows) {
            Map<String, Object> parameter = new LinkedHashMap<>();
            parameter.put(CypherQueryBuilder.ROW_POSITION, row.position());
            parameter.put(CypherQueryBuilder.LINK_FROM_PARAMETER, row.fromId());
            parameter.put(CypherQueryBuilder.LINK_TO_PARAMETER, row.toId());
            parameter.put(CypherQueryBuilder.ROW_PROPERTIES, row.properties());
            parameters.add(parameter);
        }
        return new GraphQuery(query, Map.of(CypherQueryBuilder.ROWS_PARAMETER, parameters));
    }

    /**
     * Replaces the instances a relationship property holds with the instances that were written, when the write
     * changed any of them.
     *
     * @param property The relationship property
     * @param bean     The owner
     * @param many     Whether the property holds many instances
     * @param items    The instances the property held
     * @param written  The written instances
     * @param <B>      The owner type
     * @return The owner holding the written instances
     */
    final <B> B withWritten(BeanProperty<B, Object> property, B bean, boolean many, List<Object> items, List<Object> written) {
        if (RelationshipTarget.sameItems(items, written)) {
            return bean;
        }
        Object value = many ? conversionService.convertRequired(written, property.asArgument()) : written.get(0);
        return withProperty(property, bean, value);
    }

    /**
     * Plans the query that removes the other relationships of a single-valued association, which holds one
     * relationship, so that linking a new target replaces the old. A value that is null is left alone, as it may be
     * an association that was not fetched.
     *
     * @param association The association
     * @param owner       The owning entity
     * @param target      The target entity
     * @param fromId      The identifier of the owner
     * @param toId        The identifier of the target
     * @param created     Whether the owner was only just created, so that nothing can be linked to it yet
     * @return The query, or null when there is nothing to remove
     */
    final @Nullable GraphQuery planUnlinkOthers(RuntimeAssociation<?> association,
                                                RuntimePersistentEntity<?> owner,
                                                RuntimePersistentEntity<?> target,
                                                @Nullable Object fromId,
                                                @Nullable Object toId,
                                                boolean created) {
        if (created || !association.getKind().isSingleEnded() || fromId == null || toId == null) {
            return null;
        }
        String key = "unlink:" + owner.getName() + ":" + association.getName();
        String query = queryCache.computeIfAbsent(key, k -> queryBuilder.buildUnlinkOthersQuery(association, owner, target));
        return new GraphQuery(query, Map.of(CypherQueryBuilder.LINK_FROM_PARAMETER, fromId, CypherQueryBuilder.LINK_TO_PARAMETER, toId));
    }

    /**
     * Relationship entities are not persisted on their own, so nothing else populates an identifier that is not
     * assigned by the database.
     *
     * @param associated   The relationship entity
     * @param identity     Its identity
     * @param relationship The relationship
     * @return The relationship with its identifier
     */
    private Object populateRelationshipId(RuntimePersistentEntity<Object> associated, RuntimePersistentProperty<Object> identity, Object relationship) {
        DefaultEntityEventContext<Object> event = new DefaultEntityEventContext<>(associated, relationship);
        entityEventRegistry.prePersist(event);
        Object item = event.getEntity();
        if (mapper.writeIdentity(associated, item) == null) {
            throw new DataAccessException("Relationship [" + associated.getSimpleName() + "] needs its identifier [" + identity.getName() + "] assigned before it is saved");
        }
        return item;
    }

    /**
     * Populates the identifiers of written relationship entities that are not assigned by the database. An instance
     * held twice is given one identifier.
     *
     * @param target  The target of the association
     * @param written The written relationship entities, replaced in place
     * @return The written relationship entities
     */
    final List<Object> populateRelationshipIds(RelationshipTarget target, List<Object> written) {
        RuntimePersistentProperty<Object> identity = target.identity();
        if (identity == null || GraphEntities.isNativeId(identity)) {
            return written;
        }
        Map<Object, Object> populated = new IdentityHashMap<>();
        written.replaceAll(item -> populated.computeIfAbsent(item, i -> mapper.writeIdentity(target.associated(), i) == null ? populateRelationshipId(target.associated(), identity, i) : i));
        return written;
    }

    /**
     * Plans the query that removes the other relationships of a single-valued association to relationship entities.
     * A relationship entity with an identifier can be replaced by another one to the same node, so all that may stay
     * is the relationship to the given node with the given identifier. Nothing stays of a relationship that has no
     * identifier yet.
     *
     * @param association          The association
     * @param owner                The owning entity
     * @param target               The target entity
     * @param relationshipIdentity The identity of the relationship entity
     * @param fromId               The identifier of the owner
     * @param toId                 The identifier of the target
     * @param relationshipId       The identifier of the relationship to keep, or null to keep none
     * @param created              Whether the owner was only just created, so that nothing can be linked to it yet
     * @return The query, or null when there is nothing to remove
     */
    final @Nullable GraphQuery planUnlinkOtherRelationships(RuntimeAssociation<?> association,
                                                            RuntimePersistentEntity<?> owner,
                                                            RuntimePersistentEntity<?> target,
                                                            RuntimePersistentProperty<?> relationshipIdentity,
                                                            @Nullable Object fromId,
                                                            @Nullable Object toId,
                                                            @Nullable Object relationshipId,
                                                            boolean created) {
        if (created || !association.getKind().isSingleEnded() || fromId == null || toId == null) {
            return null;
        }
        String key = "unlinkRelationships:" + owner.getName() + ":" + association.getName() + ":" + (relationshipId != null);
        String query = queryCache.computeIfAbsent(key, k -> queryBuilder.buildUnlinkOtherRelationshipsQuery(association, owner, target, relationshipId != null ? relationshipIdentity : null));
        Map<String, Object> parameters = relationshipId == null
            ? Map.of(CypherQueryBuilder.LINK_FROM_PARAMETER, fromId)
            : Map.of(CypherQueryBuilder.LINK_FROM_PARAMETER, fromId, CypherQueryBuilder.LINK_TO_PARAMETER, toId, CypherQueryBuilder.ID_PARAMETER, relationshipId);
        return new GraphQuery(query, parameters);
    }

    /**
     * Plans the query that looks up a related node with an identifier the application assigned, which says nothing
     * about whether the node exists, as a new node has one too.
     *
     * @param entity   The entity
     * @param instance The related node
     * @return The query, or null when the identifier alone decides it, see {@link #newWithoutLookup}
     */
    final @Nullable GraphQuery planFindExisting(RuntimePersistentEntity<?> entity, Object instance) {
        RuntimePersistentProperty<?> identity = entity.getIdentity();
        Object id = mapper.writeIdentity((RuntimePersistentEntity<Object>) entity, instance);
        if (identity == null || id == null || GraphEntities.isNativeId(identity)) {
            return null;
        }
        return new GraphQuery(findByIdQuery((RuntimePersistentEntity<Object>) entity), Map.of(CypherQueryBuilder.ID_PARAMETER, id));
    }

    /**
     * Whether a related node without a lookup is written for the first time: one without an identifier is, and a
     * database-assigned identifier means it was saved before.
     *
     * @param entity   The entity
     * @param instance The related node
     * @return Whether the node is new
     */
    final boolean newWithoutLookup(RuntimePersistentEntity<?> entity, Object instance) {
        return entity.getIdentity() == null || mapper.writeIdentity((RuntimePersistentEntity<Object>) entity, instance) == null;
    }

    /**
     * Plans the query that links an owner to a target node with a relationship the owner does not declare.
     *
     * @param owner  The owning entity
     * @param target The target entity
     * @param type   The relationship type
     * @param fromId The identifier of the owner
     * @param toId   The identifier of the target
     * @return The query
     */
    final GraphQuery planDynamicLink(RuntimePersistentEntity<?> owner, RuntimePersistentEntity<?> target, String type,
                               @Nullable Object fromId, @Nullable Object toId) {
        if (fromId == null || toId == null) {
            throw new DataAccessException("Cannot link a [" + type + "] relationship without the identifiers of both nodes");
        }
        try {
            // The type comes from a schemaless map and becomes part of the query text
            GraphEntities.requireName(type);
        } catch (IllegalArgumentException e) {
            throw new DataAccessException("Relationship type [" + type + "] of [" + owner.getSimpleName() + "] cannot be stored: " + e.getMessage(), e);
        }
        String key = "dynamicLink:" + owner.getName() + ":" + target.getName() + ":" + type;
        String query = queryCache.computeIfAbsent(key, k -> queryBuilder.buildDynamicLinkQuery(owner, target, type));
        return new GraphQuery(query, Map.of(CypherQueryBuilder.LINK_FROM_PARAMETER, fromId, CypherQueryBuilder.LINK_TO_PARAMETER, toId));
    }

    /**
     * Plans the query that deletes a node with its relationships.
     *
     * @param entity   The entity
     * @param instance The instance
     * @param <T>      The entity type
     * @return The query
     */
    final <T> GraphQuery planDelete(RuntimePersistentEntity<T> entity, T instance) {
        return planDelete(entity, instance, null);
    }

    /**
     * Plans the query that deletes a node with its relationships. A delete of an entity with a {@code @Version}
     * property only matches the node while it still has the version the entity was read with.
     *
     * @param entity          The entity
     * @param instance        The instance
     * @param previousVersion The version the entity was read with, or null to delete without checking it
     * @param <T>             The entity type
     * @return The query
     */
    final <T> GraphQuery planDelete(RuntimePersistentEntity<T> entity, T instance, @Nullable Object previousVersion) {
        Object id = mapper.writeIdentity(entity, instance);
        if (id == null) {
            throw new DataAccessException("Cannot delete [" + entity.getSimpleName() + "] without an identifier");
        }
        RuntimePersistentProperty<T> version = entity.hasVersion() ? entity.getVersion() : null;
        if (version != null && previousVersion != null) {
            String query = queryCache.computeIfAbsent("deleteVersioned:" + entity.getName(), k -> queryBuilder.buildDeleteVersionedNodeQuery(entity));
            return new GraphQuery(query, Map.of(CypherQueryBuilder.ID_PARAMETER, id,
                CypherQueryBuilder.VERSION_PARAMETER, mapper.writeValue(version, previousVersion)));
        }
        String query = queryCache.computeIfAbsent("delete:" + entity.getName(), k -> queryBuilder.buildDeleteByIdQuery(entity));
        return new GraphQuery(query, Map.of(CypherQueryBuilder.ID_PARAMETER, id));
    }

    /**
     * Whether two instances are the same node. Their conflict is not seen by batched queries, as an insert only
     * looks for nodes that already exist and a version check passes for both, so they are saved one at a time.
     *
     * @param entity    The entity
     * @param instances The distinct instances
     * @param <T>       The entity type
     * @return Whether an identifier is repeated
     */
    final <T> boolean repeatsIdentifier(RuntimePersistentEntity<T> entity, List<T> instances) {
        Set<Object> identifiers = new HashSet<>();
        for (T instance : instances) {
            Object id = mapper.writeIdentity(entity, instance);
            if (id != null && !identifiers.add(id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A query that writes a node.
     *
     * @param query           The query
     * @param id              The written identifier, or null when the database generates it
     * @param previousVersion The version the update checks, or null if it does not check one
     * @param insert          Whether the query inserts a versioned node with an assigned identifier, writing nothing if it exists
     * @param addLabels       The query that adds the dynamic labels of the node, or null
     */
    record NodeWrite(GraphQuery query, @Nullable Object id, @Nullable Object previousVersion, boolean insert, @Nullable String addLabels) {
    }

    /**
     * The kind of a batched node write: nodes written together set the same properties.
     *
     * @param propertyNames The stored names of the properties the nodes write
     * @param idPresent     Whether the nodes have identifiers
     * @param versioned     Whether the write checks the version of the nodes
     * @param insert        Whether the write inserts versioned nodes with assigned identifiers, writing none if one exists
     * @param batched       Whether the nodes are written with one query, or one at a time
     */
    record NodeBatch(List<String> propertyNames, boolean idPresent, boolean versioned, boolean insert, boolean batched) {
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
                    return mapper.persistedIdentity(associated, value);
                }
                return value;
            }
            return mapper.persistedValue(property, value);
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
        final Map<Object, Object> saved = new IdentityHashMap<>();
        // The nodes of a batch that are written but not yet linked, by the instance the save was called with
        final Map<Object, Runnable> unlinked = new IdentityHashMap<>();
        private final GraphSession session;
        private final OperationContext context;

        private Persister(GraphSession session, AnnotationMetadata annotationMetadata, Class<?> repositoryType) {
            this.session = session;
            this.context = new OperationContext(annotationMetadata, repositoryType);
        }

        <T> T save(RuntimePersistentEntity<T> entity, T instance, boolean insert) {
            link(instance);
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

        <T> List<T> saveAll(RuntimePersistentEntity<T> entity, Iterable<T> instances, boolean insert) {
            List<T> list = new ArrayList<>();
            instances.forEach(list::add);
            // An instance passed twice is written once, as save() does for an instance it meets again
            Set<T> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            List<T> distinct = new ArrayList<>(list.size());
            for (T instance : list) {
                if (seen.add(instance)) {
                    distinct.add(instance);
                }
            }
            if (distinct.size() < 2 || !queryBuilder.supportsBatchNodeWrites() || repeatsIdentifier(entity, distinct)) {
                List<T> results = new ArrayList<>(list.size());
                for (T instance : list) {
                    results.add(save(entity, instance, insert));
                }
                return results;
            }
            // Recorded ahead of the entity events, which may replace an immutable instance that another node of the
            // batch relates to
            for (T instance : distinct) {
                saved.put(instance, instance);
            }
            NodesOperation<T> operation = new NodesOperation<>(this, entity, distinct, insert);
            if (insert) {
                operation.persist();
            } else {
                operation.update();
            }
            List<T> written = operation.getEntities();
            Map<T, T> results = new IdentityHashMap<>();
            for (int i = 0; i < distinct.size(); i++) {
                results.put(distinct.get(i), written.get(i));
            }
            List<T> ordered = new ArrayList<>(list.size());
            for (T instance : list) {
                ordered.add(results.get(instance));
            }
            return ordered;
        }

        /**
         * Links the relationships of a node of a batch that is not yet linked, so that a node relating to it is
         * given the complete instance, as it is when it saves the related node itself.
         *
         * @param instance The instance the save was called with
         */
        void link(Object instance) {
            Runnable link = unlinked.remove(instance);
            if (link != null) {
                link.run();
            }
        }

        /**
         * Records the instance that now stands for a node whose write completed, so that a related
         * node that links back to it while its relationships are still being written sees the
         * instance carrying the generated identifier rather than the original. The two differ for
         * immutable entities such as records.
         *
         * @param instance The instance the save was called with
         * @param written  The instance after the node was written
         */
        void written(Object instance, Object written) {
            saved.put(instance, written);
        }

        /**
         * Saves the nodes an association relates an owner to, as its cascade declares: nodes already saved by this
         * persister stand for themselves, new nodes are created together and existing nodes are updated together,
         * or linked as they are when the association does not cascade to them.
         *
         * @param association The association
         * @param owner       The owning entity
         * @param entity      The target entity
         * @param targets     The target instances
         * @param <E>         The target type
         * @return The saved instances, in the order of the targets
         */
        <E> List<E> saveTargets(RuntimeAssociation<?> association, RuntimePersistentEntity<?> owner, RuntimePersistentEntity<E> entity, List<E> targets) {
            List<E> results = new ArrayList<>(Collections.nCopies(targets.size(), (E) null));
            Map<E, List<Integer>> toInsert = new IdentityHashMap<>();
            Map<E, List<Integer>> toUpdate = new IdentityHashMap<>();
            for (int i = 0; i < targets.size(); i++) {
                E target = targets.get(i);
                link(target);
                Object existing = saved.get(target);
                if (existing != null) {
                    results.set(i, (E) existing);
                } else if (RelationshipTarget.cascadesPersist(association) ? isNew(entity, target) : newWithoutLookup(entity, target)) {
                    if (!RelationshipTarget.cascadesPersist(association)) {
                        throw RelationshipTarget.targetNotPersisted(association, owner, entity);
                    }
                    toInsert.computeIfAbsent(target, k -> new ArrayList<>()).add(i);
                } else if (RelationshipTarget.cascadesUpdate(association)) {
                    toUpdate.computeIfAbsent(target, k -> new ArrayList<>()).add(i);
                } else {
                    results.set(i, target);
                }
            }
            saveGroup(entity, toInsert, true, results);
            saveGroup(entity, toUpdate, false, results);
            return results;
        }

        private <E> void saveGroup(RuntimePersistentEntity<E> entity, Map<E, List<Integer>> group, boolean insert, List<E> results) {
            RelationshipTarget.resolveSaved(group, saved, results);
            if (group.isEmpty()) {
                return;
            }
            List<E> savedTargets = saveAll(entity, new ArrayList<>(group.keySet()), insert);
            int i = 0;
            for (List<Integer> positions : group.values()) {
                E savedTarget = savedTargets.get(i++);
                for (int position : positions) {
                    results.set(position, savedTarget);
                }
            }
        }

        /**
         * Whether a related node is written for the first time, which decides the lifecycle it goes through. A
         * database-assigned identifier means it was saved before. An identifier the application assigned says
         * nothing, as a new node has one too, so the database is asked.
         *
         * @param entity   The entity
         * @param instance The related node
         * @return Whether the node is new
         */
        <E> boolean isNew(RuntimePersistentEntity<E> entity, E instance) {
            GraphQuery lookup = saved.containsKey(instance) ? null : planFindExisting(entity, instance);
            if (lookup == null) {
                return newWithoutLookup(entity, instance);
            }
            return run(lookup).records().isEmpty();
        }

        <T> int delete(RuntimePersistentEntity<T> entity, T instance) {
            DeleteNodeOperation<T> operation = new DeleteNodeOperation<>(this, entity, instance);
            operation.delete();
            return operation.deleted;
        }

        GraphResult run(GraphQuery query) {
            return session.run(query.query(), query.parameters());
        }
    }

    /**
     * Writes one node and links its relationships.
     *
     * @param <T> The entity type
     */
    private final class NodeOperation<T> extends AbstractSyncEntityOperations<OperationContext, T, RuntimeException> {
        private final Persister persister;
        private final T original;
        private @Nullable Object previousVersion;
        private boolean created;

        NodeOperation(Persister persister, RuntimePersistentEntity<T> entity, T instance, boolean insert) {
            super(persister.context, AbstractGraphRepositoryOperations.this.noCascade, entityEventRegistry, entity, AbstractGraphRepositoryOperations.this.conversionService, instance, insert);
            this.persister = persister;
            this.original = instance;
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
        protected void collectAutoPopulatedPreviousValues() {
            previousVersion = insert ? null : currentVersion(persistentEntity, entity);
        }

        @Override
        protected void failed(Exception e, String operation) {
            if (e instanceof EntityExistsException entityExists) {
                throw entityExists;
            }
            super.failed(e, operation);
        }

        @Override
        protected void execute() {
            NodeWrite write = planNodeWrite(persistentEntity, entity, insert, previousVersion);
            List<GraphRecord> records = persister.run(write.query()).records();
            Object generatedId = generatedId(persistentEntity, write, records);
            RuntimePersistentProperty<T> identity = persistentEntity.getIdentity();
            if (generatedId != null && identity != null) {
                entity = updateEntityId((BeanProperty<T, Object>) identity.getProperty(), entity, generatedId);
                created = true;
            }
            entity = new RelationshipWriter<>(persister, persistentEntity, original, entity, write.addLabels(), created).write();
        }
    }

    /**
     * Writes nodes of one entity with one query per kind of write, then links the relationships of each node.
     *
     * @param <T> The entity type
     */
    private final class NodesOperation<T> extends AbstractSyncEntitiesOperations<OperationContext, T, RuntimeException> {
        private final Persister persister;
        private final Map<Data, Object> previousVersions = new IdentityHashMap<>();
        private final Set<Data> created = Collections.newSetFromMap(new IdentityHashMap<>());
        // The instances the save was called with: the entity events and a node write may replace an immutable instance
        private final Map<Data, T> originals = new IdentityHashMap<>();

        NodesOperation(Persister persister, RuntimePersistentEntity<T> entity, Iterable<T> instances, boolean insert) {
            super(persister.context, AbstractGraphRepositoryOperations.this.noCascade, AbstractGraphRepositoryOperations.this.conversionService, entityEventRegistry, entity, instances, insert);
            this.persister = persister;
            for (Data d : entities) {
                originals.put(d, d.entity);
            }
        }

        @Override
        protected void cascadePre(Relation.Cascade cascadeType) {
            // Relationships are written after the nodes, see execute()
        }

        @Override
        protected void failed(Exception e, String operation) {
            if (e instanceof EntityExistsException entityExists) {
                throw entityExists;
            }
            super.failed(e, operation);
        }

        @Override
        protected void cascadePost(Relation.Cascade cascadeType) {
            // Relationships are written after the nodes, see execute()
        }

        @Override
        protected void collectAutoPopulatedPreviousValues() {
            if (insert) {
                return;
            }
            for (Data d : entities) {
                Object version = currentVersion(persistentEntity, d.entity);
                if (version != null) {
                    previousVersions.put(d, version);
                }
            }
        }

        @Override
        protected void execute() {
            List<Data> pending = new ArrayList<>();
            for (Data d : entities) {
                if (!d.vetoed) {
                    pending.add(d);
                }
            }
            Map<NodeBatch, List<Data>> batches = new LinkedHashMap<>();
            Map<Data, @Nullable String> addLabels = new IdentityHashMap<>();
            for (Data d : pending) {
                // Checked ahead of the writes, so that a label that cannot be stored leaves the nodes as they were
                addLabels.put(d, addLabelsQuery(persistentEntity, d.entity));
                NodeBatch batch = planNodeBatch(persistentEntity, d.entity, insert, previousVersions.get(d));
                batches.computeIfAbsent(batch, k -> new ArrayList<>()).add(d);
            }
            batches.forEach(this::writeNodes);
            // A node whose relationships are linked first may point to a node later in the batch, which must be
            // found with the identifier it was just given: an immutable instance was replaced by the write
            for (Data d : pending) {
                T original = Objects.requireNonNull(originals.get(d));
                persister.written(original, d.entity);
                persister.unlinked.put(original, () -> {
                    d.entity = new RelationshipWriter<>(persister, persistentEntity, original, d.entity, addLabels.get(d), created.contains(d)).write();
                    persister.saved.put(original, d.entity);
                    persister.saved.put(d.entity, d.entity);
                });
            }
            for (Data d : pending) {
                persister.link(Objects.requireNonNull(originals.get(d)));
            }
        }

        private void writeNodes(NodeBatch batch, List<Data> nodes) {
            if (!batch.batched()) {
                for (Data d : nodes) {
                    NodeWrite write = planNodeWrite(persistentEntity, d.entity, insert, previousVersions.get(d));
                    generatedId(persistentEntity, write, persister.run(write.query()).records());
                }
                return;
            }
            List<Map<String, @Nullable Object>> rows = new ArrayList<>(nodes.size());
            for (int i = 0; i < nodes.size(); i++) {
                Data d = nodes.get(i);
                rows.add(nodeRow(persistentEntity, d.entity, i, batch.versioned() ? previousVersions.get(d) : null));
            }
            List<GraphRecord> records = persister.run(new GraphQuery(batchQuery(persistentEntity, batch), Map.of(CypherQueryBuilder.ROWS_PARAMETER, rows))).records();
            checkBatchWritten(persistentEntity, batch, rows, records.size());
            RuntimePersistentProperty<T> identity = persistentEntity.getIdentity();
            if (batch.idPresent() || identity == null || !GraphEntities.isNativeId(identity)) {
                return;
            }
            for (GraphRecord graphRecord : records) {
                Data d = nodes.get(((Number) Objects.requireNonNull(graphRecord.get(CypherQueryBuilder.ROW_POSITION))).intValue());
                d.entity = updateEntityId(identity.getProperty(), d.entity, batchGeneratedId(persistentEntity, graphRecord));
                created.add(d);
            }
        }
    }

    /**
     * Adds the dynamic labels of a written node and links its relationships, saving the related nodes.
     *
     * @param <T> The entity type
     */
    private final class RelationshipWriter<T> {
        private final Persister persister;
        private final RuntimePersistentEntity<T> persistentEntity;
        private final T original;
        private final @Nullable String addLabels;
        private final boolean created;
        private T entity;

        /**
         * @param persister        The persister
         * @param persistentEntity The entity
         * @param original         The instance the save was called with
         * @param entity           The instance after the node was written, which differs from the original
         *                         for an immutable entity that received a generated identifier
         * @param addLabels        The query that adds the dynamic labels of the node, or null
         * @param created          Whether the node was only just created
         */
        RelationshipWriter(Persister persister, RuntimePersistentEntity<T> persistentEntity, T original, T entity, @Nullable String addLabels, boolean created) {
            this.persister = persister;
            this.persistentEntity = persistentEntity;
            this.original = original;
            this.entity = entity;
            this.addLabels = addLabels;
            this.created = created;
        }

        T write() {
            GraphQuery labels = planAddLabels(persistentEntity, addLabels, entity);
            if (labels != null) {
                persister.run(labels);
            }
            persister.written(original, entity);
            writeRelationships();
            writeDynamicRelationships();
            return entity;
        }

        private void writeRelationships() {
            for (RuntimePersistentProperty<T> property : persistentEntity.getPersistentProperties()) {
                if (property instanceof RuntimeAssociation<T> association && GraphEntities.isRelationship(association)) {
                    writeRelationship(association);
                }
            }
        }

        /**
         * Saves the nodes an association relates the entity to, then merges its relationships with one query per
         * set of relationship properties.
         */
        private void writeRelationship(RuntimeAssociation<T> association) {
            BeanProperty<T, Object> beanProperty = association.getProperty();
            Object value = beanProperty.get(entity);
            List<Object> items = RelationshipTarget.relatedItems(value);
            if (items.isEmpty()) {
                return;
            }
            RelationshipTarget target = RelationshipTarget.of(association);
            List<Object> targets = target.targets(items);
            List<Object> savedTargets = persister.saveTargets(association, persistentEntity, target.entity(), targets);
            Object fromId = mapper.writeIdentity(persistentEntity, entity);
            List<Object> written = populateRelationshipIds(target, target.written(items, savedTargets));
            List<RelationshipTarget.LinkRow> rows = target.linkRows(association, mapper, fromId, written, savedTargets);
            RuntimePersistentProperty<Object> identity = target.identity();
            // A single-valued association has one row
            if (identity == null) {
                unlinkOthers(association, target.entity(), fromId, rows.get(0).toId());
                runLinks(association, target.entity(), rows);
            } else {
                unlinkOtherRelationships(association, target.entity(), identity, fromId, rows.get(0).toId(), mapper.writeIdentity(target.associated(), written.get(0)));
                runIdentifiedLinks(association, target, rows, written);
            }
            entity = withWritten(beanProperty, entity, value instanceof Iterable<?>, items, written);
        }

        /**
         * Merges the relationships of the rows, which have no identifier: the rows that write the same properties
         * with one query, when the database supports it, and the others one at a time.
         */
        private void runLinks(RuntimeAssociation<T> association, RuntimePersistentEntity<?> target, List<RelationshipTarget.LinkRow> rows) {
            for (Map.Entry<List<String>, List<RelationshipTarget.LinkRow>> group : RelationshipTarget.groupLinkRows(rows).entrySet()) {
                List<RelationshipTarget.LinkRow> batch = group.getValue();
                if (batch.size() > 1 && queryBuilder.supportsBatchedLinks()) {
                    GraphQuery query = planLinks(association, persistentEntity, target, batch, group.getKey());
                    RelationshipTarget.checkLinked(association, persister.run(query).records(), batch.size());
                } else {
                    for (RelationshipTarget.LinkRow row : batch) {
                        runLink(association, target, row.toId(), row.fromId(), row.properties(), null, RelationshipTarget.LinkMode.MERGE, null);
                    }
                }
            }
        }

        /**
         * Writes relationship entities with an identifier one at a time, as several of them may connect the same
         * nodes. One that was not saved, or that no longer connects these nodes, is a new one. Saving one that was
         * saved finds it by its identifier from either end, even if it is undirected. An instance held twice is one
         * relationship.
         *
         * @param written The written relationship entities, replaced in place with those holding their identifiers
         */
        private void runIdentifiedLinks(RuntimeAssociation<T> association, RelationshipTarget target, List<RelationshipTarget.LinkRow> rows, List<Object> written) {
            Map<Object, Object> linked = new IdentityHashMap<>();
            for (RelationshipTarget.LinkRow row : rows) {
                Object item = written.get(row.position());
                Object done = linked.get(item);
                if (done == null) {
                    Object savedId = mapper.writeIdentity(target.associated(), item);
                    Object id = savedId == null ? null : runLink(association, target.entity(), row.toId(), row.fromId(), row.properties(), target.identity(), RelationshipTarget.LinkMode.UPDATE, savedId);
                    if (id == null) {
                        id = runLink(association, target.entity(), row.toId(), row.fromId(), row.properties(), target.identity(), RelationshipTarget.LinkMode.CREATE, null);
                    }
                    done = target.withRelationshipId(mapper, item, id);
                    linked.put(item, done);
                }
                written.set(row.position(), done);
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
                    if (target instanceof Map<?, ?>) {
                        // A node of a label no entity maps reads as its properties, and it is already linked
                        savedTargets.add(target);
                        continue;
                    }
                    RuntimePersistentEntity<Object> targetEntity = (RuntimePersistentEntity<Object>) AbstractGraphRepositoryOperations.this.getEntity(target.getClass());
                    Object savedTarget = persister.save(targetEntity, target, persister.isNew(targetEntity, target));
                    changed |= savedTarget != target;
                    savedTargets.add(savedTarget);
                    persister.run(planDynamicLink(persistentEntity, targetEntity, type, fromId, mapper.writeIdentity(targetEntity, savedTarget)));
                }
                written.put(type, savedTargets);
            }
            if (changed) {
                entity = withProperty((BeanProperty<T, Object>) (BeanProperty<?, ?>) property, entity, written);
            }
        }

        /**
         * A single-valued association holds one relationship, so linking a new target replaces the old. A value
         * that is null is left alone, as it may be an association that was not fetched. Nothing can be linked yet
         * to a node that was only just created.
         */
        private void unlinkOthers(RuntimeAssociation<T> association, RuntimePersistentEntity<?> target, @Nullable Object fromId, @Nullable Object toId) {
            GraphQuery query = planUnlinkOthers(association, persistentEntity, target, fromId, toId, created);
            if (query != null) {
                persister.run(query).records();
            }
        }

        private void unlinkOtherRelationships(RuntimeAssociation<T> association,
                                              RuntimePersistentEntity<?> target,
                                              RuntimePersistentProperty<?> relationshipIdentity,
                                              @Nullable Object fromId,
                                              @Nullable Object toId,
                                              @Nullable Object relationshipId) {
            GraphQuery query = planUnlinkOtherRelationships(association, persistentEntity, target, relationshipIdentity, fromId, toId, relationshipId, created);
            if (query != null) {
                persister.run(query);
            }
        }

        private @Nullable Object runLink(RuntimeAssociation<T> association,
                                         RuntimePersistentEntity<?> target,
                                         @Nullable Object toId,
                                         @Nullable Object fromId,
                                         Map<String, Object> properties,
                                         @Nullable RuntimePersistentProperty<?> relationshipIdentity,
                                         RelationshipTarget.LinkMode mode,
                                         @Nullable Object relationshipId) {
            GraphQuery query = planLink(association, persistentEntity, target, fromId, toId, properties, relationshipIdentity, mode, relationshipId);
            return RelationshipTarget.linkedRelationshipId(association, mode, persister.run(query).records());
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
        private @Nullable Object previousVersion;

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
        protected void collectAutoPopulatedPreviousValues() {
            previousVersion = currentVersion(persistentEntity, entity);
        }

        @Override
        protected void execute() {
            deleted = updateCount(persister.run(planDelete(persistentEntity, entity, previousVersion))).intValue();
            checkVersionedDelete(persistentEntity, previousVersion, deleted);
        }
    }
}
