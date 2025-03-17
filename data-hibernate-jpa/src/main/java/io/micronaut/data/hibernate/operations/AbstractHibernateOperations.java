/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.data.hibernate.operations;

import io.micronaut.aop.InvocationContext;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.QueryHint;
import io.micronaut.data.jpa.annotation.EntityGraph;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Pageable.Mode;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.query.builder.jpa.JpaQueryBuilder;
import io.micronaut.data.model.runtime.PagedQuery;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.operations.HintsCapableRepository;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.mapper.BeanIntrospectionMapper;
import io.micronaut.data.runtime.operations.internal.query.BindableParametersPreparedQuery;
import io.micronaut.data.runtime.operations.internal.query.BindableParametersStoredQuery;
import io.micronaut.data.runtime.operations.internal.query.DefaultBindableParametersPreparedQuery;
import io.micronaut.data.runtime.operations.internal.query.DefaultBindableParametersStoredQuery;
import io.micronaut.data.runtime.query.PreparedQueryDecorator;
import io.micronaut.data.runtime.query.StoredQueryDecorator;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.Tuple;
import jakarta.persistence.TupleElement;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.hibernate.graph.AttributeNode;
import org.hibernate.graph.Graph;
import org.hibernate.graph.RootGraph;
import org.hibernate.graph.SubGraph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Abstract Hibernate operations shared between the synchronous and the reactive implementations.
 *
 * @param <S> The session type
 * @param <Q> The query type
 * @param <P> The selection query type
 * @author Denis Stepanov
 * @since 3.5.0
 */
@Internal
public abstract class AbstractHibernateOperations<S, Q, P extends Q> implements HintsCapableRepository, PreparedQueryDecorator, StoredQueryDecorator {

    private static final JpaQueryBuilder QUERY_BUILDER = new JpaQueryBuilder();
    private static final String ENTITY_GRAPH_FETCH = "jakarta.persistence.fetchgraph";
    private static final String ENTITY_GRAPH_LOAD = "jakarta.persistence.loadgraph";

    protected final ConversionService dataConversionService;
    protected final RuntimeEntityRegistry runtimeEntityRegistry;

    /**
     * Default constructor.
     *
     * @param runtimeEntityRegistry The runtime entity registry
     * @param dataConversionService The data conversion service
     */
    protected AbstractHibernateOperations(RuntimeEntityRegistry runtimeEntityRegistry, DataConversionService dataConversionService) {
        this.runtimeEntityRegistry = runtimeEntityRegistry;
        // Backwards compatibility should be removed in the next version
        this.dataConversionService = dataConversionService == null ? ConversionService.SHARED : dataConversionService;
    }

    @Override
    public <E, R> PreparedQuery<E, R> decorate(PreparedQuery<E, R> preparedQuery) {
        return new DefaultBindableParametersPreparedQuery<>(preparedQuery);
    }

    @Override
    public <E, R> StoredQuery<E, R> decorate(StoredQuery<E, R> storedQuery) {
        RuntimePersistentEntity<E> runtimePersistentEntity = runtimeEntityRegistry.getEntity(storedQuery.getRootEntity());
        return new DefaultBindableParametersStoredQuery<>(storedQuery, runtimePersistentEntity);
    }

    /**
     * @return The application context
     */
    protected ApplicationContext getApplicationContext() {
        return runtimeEntityRegistry.getApplicationContext();
    }

    /**
     * @return The conversion service
     */
    protected ConversionService getConversionService() {
        return dataConversionService;
    }

    /**
     * Gets the persistence entity.
     *
     * @param type The entity type
     * @param <T>  The entity type
     * @return The persistent entity
     */
    @NonNull
    protected abstract <T> RuntimePersistentEntity<T> getEntity(@NonNull Class<T> type);

    @Override
    @NonNull
    public Map<String, Object> getQueryHints(@NonNull StoredQuery<?, ?> storedQuery) {
        AnnotationMetadata annotationMetadata = storedQuery.getAnnotationMetadata();
        if (annotationMetadata.hasAnnotation(EntityGraph.class)) {
            String hint = annotationMetadata.stringValue(EntityGraph.class, "hint").orElse(ENTITY_GRAPH_FETCH);
            String graphName = annotationMetadata.stringValue(EntityGraph.class).orElse(null);
            String[] paths = annotationMetadata.stringValues(EntityGraph.class, "attributePaths");
            // If the graphName is set, it takes precedence over the attributeNames
            if (graphName != null) {
                return Collections.singletonMap(hint, graphName);
            } else if (ArrayUtils.isNotEmpty(paths)) {
                return Collections.singletonMap(hint, paths);
            }
        }
        return Collections.emptyMap();
    }

    /**
     * Sets a parameter into query.
     *
     * @param query         The query
     * @param parameterName The parameter name
     * @param value         The value
     */
    protected abstract void setParameter(Q query, String parameterName, Object value);

    /**
     * Sets parameter into query.
     *
     * @param query         The query
     * @param parameterName The parameter name
     * @param value         The value
     * @param argument      The argument
     */
    protected abstract void setParameter(Q query, String parameterName, Object value, Argument<?> argument);

    /**
     * Sets a list parameter into query.
     *
     * @param query         The query
     * @param parameterName The parameter name
     * @param value         The value
     */
    protected abstract void setParameterList(Q query, String parameterName, Collection<Object> value);

    /**
     * Sets a list parameter into query.
     *
     * @param query         The query
     * @param parameterName The parameter name
     * @param value         The value
     * @param argument      The argument
     */
    protected abstract void setParameterList(Q query, String parameterName, Collection<Object> value, Argument<?> argument);

    /**
     * Sets a parameter into query.
     *
     * @param query          The query
     * @param parameterIndex The parameter index
     * @param value          The value
     */
    protected abstract void setParameter(Q query, int parameterIndex, Object value);

    /**
     * Sets parameter into query.
     *
     * @param query          The query
     * @param parameterIndex The parameter index
     * @param value          The value
     * @param argument       The argument
     */
    protected abstract void setParameter(Q query, int parameterIndex, Object value, Argument<?> argument);

    /**
     * Sets a list parameter into query.
     *
     * @param query          The query
     * @param parameterIndex The parameter index
     * @param value          The value
     */
    protected abstract void setParameterList(Q query, int parameterIndex, Collection<Object> value);

    /**
     * Sets a list parameter into query.
     *
     * @param query          The query
     * @param parameterIndex The parameter index
     * @param value          The value
     * @param argument       The argument
     */
    protected abstract void setParameterList(Q query, int parameterIndex, Collection<Object> value, Argument<?> argument);

    /**
     * Sets a hint.
     *
     * @param query    The query
     * @param hintName The hint name
     * @param value    The value
     */
    protected abstract void setHint(P query, String hintName, Object value);

    /**
     * Sets the max results value.
     *
     * @param query The query
     * @param max   The max value
     */
    protected abstract void setMaxResults(P query, int max);

    /**
     * Sets the offset value.
     *
     * @param query  The query
     * @param offset The offset value
     */
    protected abstract void setOffset(P query, int offset);

    /**
     * Gets an entity graph.
     *
     * @param session    The session
     * @param entityType The entity type
     * @param graphName  The graph name
     * @param <T>        The entity type
     * @return The graph
     */
    protected abstract <T> jakarta.persistence.EntityGraph<T> getEntityGraph(S session, Class<T> entityType, String graphName);

    /**
     * Creates an entity graph.
     *
     * @param session    The session
     * @param entityType The entityType
     * @param <T>        The entityType
     * @return The graph
     */
    protected abstract <T> jakarta.persistence.EntityGraph<T> createEntityGraph(S session, Class<T> entityType);

    /**
     * Create a new query.
     *
     * @param session    The session
     * @param query      The query
     * @param resultType The result type
     * @return new query
     */
    protected abstract P createQuery(S session, String query, @Nullable Class<?> resultType);

    /**
     * Create a new native query.
     *
     * @param session    The session
     * @param query      The query
     * @param resultType The result type
     * @return new query
     */
    protected abstract P createNativeQuery(S session, String query, Class<?> resultType);

    /**
     * Create a native query.
     *
     * @param session       The session
     * @param criteriaQuery The criteriaQuery
     * @return new query
     */
    protected abstract P createQuery(S session, CriteriaQuery<?> criteriaQuery);

    /**
     * Collect one result.
     *
     * @param session       The session
     * @param preparedQuery The prepared query
     * @param collector     The collector
     * @param <R>           The result type
     */
    protected <R> void collectFindOne(S session, PreparedQuery<?, R> preparedQuery, ResultCollector<R> collector) {
        String query = preparedQuery.getQuery();
        collectResults(session, query, preparedQuery, preparedQuery.getPageable(), collector);
    }

    /**
     * Collect all results.
     *
     * @param session       The session
     * @param preparedQuery The prepared query
     * @param collector     The collector
     * @param <R>           The result type
     */
    protected <R> void collectFindAll(S session, PreparedQuery<?, R> preparedQuery, ResultCollector<R> collector) {
        String queryStr = preparedQuery.getQuery();
        Pageable pageable = preparedQuery.getPageable();
        if (pageable != Pageable.UNPAGED) {
            if (pageable.getMode() != Mode.OFFSET) {
                throw new UnsupportedOperationException("Pageable mode " + pageable.getMode() + " is not supported by hibernate operations");
            }
            Sort sort = pageable.getSort();
            if (sort.isSorted()) {
                queryStr += QUERY_BUILDER.buildOrderBy(queryStr, getEntity(preparedQuery.getRootEntity()), AnnotationMetadata.EMPTY_METADATA, sort,
                    preparedQuery.isNative()).getQuery();
            }
            if (preparedQuery.isNative()) {
                // Native queries don't support setting the order
                pageable = pageable.withoutSort();
            }
        }
        collectResults(session, queryStr, preparedQuery, pageable, collector);
    }

    private <T, R> void collectResults(S session,
                                       String queryStr,
                                       PreparedQuery<T, R> preparedQuery,
                                       Pageable pageable,
                                       ResultCollector<R> resultCollector) {
        if (preparedQuery.isDtoProjection()) {
            P q;
            if (preparedQuery.isNative()) {
                q = createNativeQuery(session, queryStr, Tuple.class);
            } else if (queryStr.toLowerCase(Locale.ENGLISH).startsWith("select new ")) {
                @SuppressWarnings("unchecked") Class<R> wrapperType = (Class<R>) ReflectionUtils.getWrapperType(preparedQuery.getResultType());
                P query = createQuery(session, queryStr, wrapperType);
                bindPreparedQuery(query, preparedQuery, pageable, session);
                resultCollector.collect(query);
                return;
            } else {
                q = createQuery(session, queryStr, Tuple.class);
            }
            bindPreparedQuery(q, preparedQuery, pageable, session);
            resultCollector.collectTuple(q, tuple -> {
                Set<String> properties = tuple.getElements().stream().map(TupleElement::getAlias).collect(Collectors.toCollection(() -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)));
                return (new BeanIntrospectionMapper<Tuple, R>() {
                    @Override
                    public Object read(Tuple tuple1, String alias) {
                        if (!properties.contains(alias)) {
                            return null;
                        }
                        return tuple1.get(alias);
                    }

                    @Override
                    public ConversionService getConversionService() {
                        return dataConversionService;
                    }

                }).map(tuple, preparedQuery.getResultType());
            });
        } else {
            @SuppressWarnings("unchecked") Class<R> wrapperType = (Class<R>) ReflectionUtils.getWrapperType(preparedQuery.getResultType());
            P q;
            if (preparedQuery.isNative()) {
                Class<T> rootEntity = preparedQuery.getRootEntity();
                if (wrapperType != rootEntity) {
                    P nativeQuery = createNativeQuery(session, queryStr, Tuple.class);
                    bindPreparedQuery(nativeQuery, preparedQuery, pageable, session);
                    resultCollector.collectTuple(nativeQuery, tuple -> {
                        Object o = tuple.get(0);
                        if (wrapperType.isInstance(o)) {
                            return (R) o;
                        }
                        return dataConversionService.convertRequired(o, wrapperType);
                    });
                    return;
                } else {
                    q = createNativeQuery(session, queryStr, wrapperType);
                }
            } else {
                q = createQuery(session, queryStr, wrapperType);
            }
            bindPreparedQuery(q, preparedQuery, pageable, session);
            resultCollector.collect(q);
        }
    }

    /**
     * Bind parameters into query.
     *
     * @param q             The query
     * @param preparedQuery The prepared query
     * @param bindNamed     If parameter should be bind by the name
     * @param <T>           The entity type
     * @param <R>           The result type
     */
    protected <T, R> void bindParameters(Q q, @NonNull PreparedQuery<T, R> preparedQuery, boolean bindNamed) {
        BindableParametersPreparedQuery<T, R> bindableParametersPreparedQuery = getBindableParametersPreparedQuery(preparedQuery);
        BindableParametersStoredQuery.Binder binder = createBinder(q, preparedQuery.getArguments(), bindNamed);
        bindableParametersPreparedQuery.bindParameters(binder);
    }

    /**
     * Bind parameters into query.
     *
     * @param <T>               The entity type
     * @param <R>               The result type
     * @param q                 The query
     * @param storedQuery       The stored query
     * @param invocationContext The invocationContext
     * @param entity            The entity
     */
    protected <T, R> void bindParameters(Q q, @NonNull StoredQuery<T, R> storedQuery,
                                         InvocationContext<?, ?> invocationContext,
                                         T entity) {
        BindableParametersStoredQuery<T, R> bindableParametersPreparedQuery = (BindableParametersStoredQuery<T, R>) storedQuery;
        BindableParametersStoredQuery.Binder binder = createBinder(q, invocationContext.getArguments(), true);
        bindableParametersPreparedQuery.bindParameters(binder, invocationContext, entity, null);
    }

    private BindableParametersStoredQuery.Binder createBinder(Q q, Argument<?>[] arguments, boolean bindNamed) {
        return new BindableParametersStoredQuery.Binder() {

            int index = 1;

            @Override
            public Object autoPopulateRuntimeProperty(RuntimePersistentProperty<?> persistentProperty, Object previousValue) {
                return runtimeEntityRegistry.autoPopulateRuntimeProperty(persistentProperty, previousValue);
            }

            @Override
            public Object convert(Object value, RuntimePersistentProperty<?> property) {
                return value;
            }

            @Override
            public Object convert(Class<?> converterClass, Object value, Argument<?> argument) {
                return value;
            }

            @Override
            public void bindOne(QueryParameterBinding binding, Object value) {
                String parameterName = Objects.requireNonNull(binding.getName(), "Parameter name cannot be null!");
                if (binding.getParameterIndex() != -1) {
                    int parameterIndex = binding.getParameterIndex();
                    Argument<?> argument = arguments[parameterIndex];
                    Class<?> argumentType = argument.getType();
                    if (Collection.class.isAssignableFrom(argumentType)) {
                        if (bindNamed) {
                            setParameterList(q, parameterName, value == null ? Collections.emptyList() : (Collection<Object>) value, argument.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT));
                        } else {
                            setParameterList(q, index, value == null ? Collections.emptyList() : (Collection<Object>) value, argument.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT));
                        }
                    } else if (Object[].class.isAssignableFrom(argumentType)) {
                        Collection<Object> coll;
                        if (value == null) {
                            coll = Collections.emptyList();
                        } else if (value instanceof Collection collection) {
                            coll = collection;
                        } else {
                            coll = Arrays.asList((Object[]) value);
                        }
                        if (bindNamed) {
                            setParameterList(q, parameterName, coll);
                        } else {
                            setParameterList(q, index, coll);
                        }
                    } else if (bindNamed) {
                        setParameter(q, parameterName, value, argument);
                    } else {
                        setParameter(q, index, value, argument);
                    }
                } else if (bindNamed) {
                    setParameter(q, parameterName, value);
                } else {
                    setParameter(q, index, value);
                }
                index++;
            }

            @Override
            public void bindMany(QueryParameterBinding binding, Collection<Object> values) {
                bindOne(binding, values);
            }

        };
    }

    private <T, R> void bindPreparedQuery(P q, @NonNull PreparedQuery<T, R> preparedQuery, Pageable pageable, S currentSession) {
        bindParameters(q, preparedQuery, true);
        bindPageable(q, pageable);
        bindQueryHints(q, preparedQuery, currentSession);
    }

    private <T> void bindQueryHints(P q, @NonNull PagedQuery<T> preparedQuery, @NonNull S session) {
        Map<String, Object> queryHints = preparedQuery.getQueryHints();
        if (CollectionUtils.isNotEmpty(queryHints)) {
            for (Map.Entry<String, Object> entry : queryHints.entrySet()) {
                String hintName = entry.getKey();
                Object value = entry.getValue();
                if (ENTITY_GRAPH_FETCH.equals(hintName) || ENTITY_GRAPH_LOAD.equals(hintName)) {
                    String graphName = preparedQuery.getAnnotationMetadata().stringValue(EntityGraph.class).orElse(null);
                    if (graphName != null) {
                        jakarta.persistence.EntityGraph<?> entityGraph = getEntityGraph(session, preparedQuery.getRootEntity(), graphName);
                        setHint(q, hintName, entityGraph);
                    } else if (value instanceof String[] pathsDefinitions) {
                        if (ArrayUtils.isNotEmpty(pathsDefinitions)) {
                            RootGraph<T> entityGraph = createGraph(pathsDefinitions, session, preparedQuery.getRootEntity());
                            setHint(q, hintName, entityGraph);
                        }
                    }
                } else {
                    setHint(q, hintName, value);
                }
            }
        }
    }

    /**
     * Create an EntityGraph from the collection of paths provided. It ensures that only one SubGraph for each component
     * of the path is created within the graph.
     *
     * @param paths      Array of paths to add to the EntityGraph
     * @param session    The hibernate session
     * @param rootEntity The root entity class
     * @param <T>        The entity type
     * @return A RootGraph created from the paths
     */
    private <T> RootGraph<T> createGraph(@NonNull String[] paths, @NonNull S session, @NonNull Class<T> rootEntity) {
        RootGraph<T> rootGraph = (RootGraph<T>) createEntityGraph(session, rootEntity);
        for (String path : paths) {
            if (path.trim().isEmpty()) {
                continue;
            }
            String[] parts = path.split("\\.");
            if (parts.length == 1) {
                AttributeNode<?> attrNode = rootGraph.findAttributeNode(path);
                if (attrNode == null) {
                    rootGraph.addAttributeNode(path);
                }
            } else {
                Graph<?> graph = rootGraph;
                for (int i = 0; i < parts.length; i++) {
                    String part = parts[i];
                    // Check if the node already exists at this level
                    AttributeNode<?> attrNode = graph.findAttributeNode(part);
                    if (attrNode != null) {
                        SubGraph<?> subGraph = attrNode.getSubGraphs().isEmpty() ? null : attrNode.getSubGraphs().values().iterator().next();
                        // If this is not a leaf and the subgraph doesn't exist, create it
                        if (subGraph == null && i < parts.length - 1) {
                            graph = graph.addSubGraph(part);
                        } else if (subGraph != null) {
                            // Otherwise, keep the existing one for the child node
                            graph = subGraph;
                        }
                    } else if (i == parts.length - 1) {
                        // If this is a leaf, create an attribute node
                        graph.addAttributeNode(part);
                    } else {
                        // Otherwise, create a subgraph
                        graph = graph.addSubGraph(part);
                    }
                }
            }
        }
        return rootGraph;
    }

    protected final FlushModeType getFlushModeType(AnnotationMetadata annotationMetadata) {
        return annotationMetadata.getAnnotationValuesByType(QueryHint.class).stream().filter(av -> FlushModeType.class.getName().equals(av.stringValue("name").orElse(null))).map(av -> av.enumValue("value", FlushModeType.class)).findFirst().orElse(Optional.empty()).orElse(null);
    }

    private void bindPageable(P q, @NonNull Pageable pageable) {
        if (pageable == Pageable.UNPAGED) {
            // no pagination
            return;
        }
        if (pageable.getMode() != Mode.OFFSET) {
            throw new UnsupportedOperationException("Pageable mode " + pageable.getMode() + " is not supported by hibernate operations");
        }

        int max = pageable.getSize();
        if (max > 0) {
            setMaxResults(q, max);
        }
        long offset = pageable.getOffset();
        if (offset > 0) {
            setOffset(q, (int) offset);
        }
    }

    protected final <T> void collectPagedResults(CriteriaBuilder criteriaBuilder, S session, PagedQuery<T> pagedQuery, ResultCollector<T> resultCollector) {
        Pageable pageable = pagedQuery.getPageable();
        Class<T> entity = pagedQuery.getRootEntity();
        CriteriaQuery<T> query = criteriaBuilder.createQuery(pagedQuery.getRootEntity());
        Root<T> root = query.from(entity);
        bindCriteriaSort(query, root, criteriaBuilder, pageable);
        P q = createQuery(session, query);
        bindPageable(q, pageable.withoutSort());
        bindQueryHints(q, pagedQuery, session);
        resultCollector.collect(q);
    }

    protected final <R> void collectCountOf(CriteriaBuilder criteriaBuilder, S session, Class<R> entity, @Nullable Pageable pageable, ResultCollector<Long> resultCollector) {
        CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
        countQuery.select(criteriaBuilder.count(countQuery.from(entity)));
        P countQ = createQuery(session, countQuery);
        if (pageable != null) {
            bindPageable(countQ, pageable.withoutSort());
        }
        resultCollector.collect(countQ);
    }

    private <T> void bindCriteriaSort(CriteriaQuery<T> criteriaQuery, Root<?> root, CriteriaBuilder builder, @NonNull Sort sort) {
        List<Order> orders = new ArrayList<>();
        for (Sort.Order order : sort.getOrderBy()) {
            Path<?> path = root;
            for (String property : StringUtils.splitOmitEmptyStrings(order.getProperty(), '.')) {
                path = path.get(property);
            }
            Expression<?> expression = order.isIgnoreCase() ? builder.lower(path.type().as(String.class)) : path;
            orders.add(order.isAscending() ? builder.asc(expression) : builder.desc(expression));
        }
        criteriaQuery.orderBy(orders);
    }

    private <E, R> BindableParametersPreparedQuery<E, R> getBindableParametersPreparedQuery(PreparedQuery<E, R> preparedQuery) {
        if (preparedQuery instanceof BindableParametersPreparedQuery<E, R> bindableParametersPreparedQuery) {
            return bindableParametersPreparedQuery;
        }
        throw new IllegalStateException("Expected for prepared query to be of type: BindableParametersPreparedQuery");
    }

    /**
     * The result collector.
     *
     * @param <R> The result value.
     * @author Denis Stepanov
     * @since 3.5.0
     */
    protected abstract class ResultCollector<R> {

        /**
         * Collect a tuple from the query and remap it.
         *
         * @param query The query
         * @param fn    The map function
         */
        protected abstract void collectTuple(P query, Function<Tuple, R> fn);

        /**
         * Collect a value from the query.
         *
         * @param query The query
         */
        protected abstract void collect(P query);

    }

}
