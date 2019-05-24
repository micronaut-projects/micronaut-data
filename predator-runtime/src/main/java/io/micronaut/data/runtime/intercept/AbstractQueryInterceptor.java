/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.runtime.intercept;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.intercept.PredatorInterceptor;
import io.micronaut.data.intercept.annotation.PredatorMethod;
import io.micronaut.data.model.*;
import io.micronaut.data.backend.Datastore;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.jpa.JpaQueryBuilder;
import io.micronaut.inject.ExecutableMethod;

import javax.annotation.Nonnull;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Abstract interceptor that executes a {@link io.micronaut.data.annotation.Query}.
 * @param <T> The declaring type
 * @param <R> The return type
 * @since 1.0
 * @author graemerocher
 */
abstract class AbstractQueryInterceptor<T, R> implements PredatorInterceptor<T, R> {
    protected final Datastore datastore;
    private final ConcurrentMap<Class, Class> lastUpdatedTypes = new ConcurrentHashMap<>(10);
    private final ConcurrentMap<ExecutableMethod, StoredQuery> findQueries = new ConcurrentHashMap<>(50);
    private final ConcurrentMap<ExecutableMethod, StoredQuery> countQueries = new ConcurrentHashMap<>(50);

    /**
     * Default constructor.
     * @param datastore The datastore
     */
    AbstractQueryInterceptor(@NonNull Datastore datastore) {
        ArgumentUtils.requireNonNull("datastore", datastore);
        this.datastore = datastore;
    }

    /**
     * Prepares a query for the given context.
     * @param context The context
     * @return The query
     */
    protected final PreparedQuery<?, ?> prepareQuery(MethodInvocationContext<T, R> context) {
        return prepareQuery(context, null);
    }

    /**
     * Prepares a query for the given context.
     * @param context The context
     * @param resultType The result type
     * @return The query
     * @param <RT> The result generic type
     */
    protected final <RT> PreparedQuery<?, RT> prepareQuery(MethodInvocationContext<T, R> context, Class<RT> resultType) {

        ExecutableMethod<T, R> executableMethod = context.getExecutableMethod();
        StoredQuery<?, RT> storedQuery = findQueries.get(executableMethod);
        if (storedQuery == null) {
            Class<?> rootEntity = context.classValue(PredatorMethod.class, PredatorMethod.META_MEMBER_ROOT_ENTITY)
                    .orElseThrow(() -> new IllegalStateException("No root entity present in method"));
            if (resultType == null) {
                //noinspection unchecked
                resultType = (Class<RT>) context.classValue(PredatorMethod.class, PredatorMethod.META_MEMBER_RESULT_TYPE).orElse(rootEntity);
            }
            String query = context.stringValue(Query.class).orElseThrow(() ->
                    new IllegalStateException("No query present in method")
            );
            Map<String, String> parameterValues = buildParameterBinding(context);
            storedQuery = new DefaultStoredQuery<>(
                    executableMethod,
                    resultType,
                    rootEntity,
                    query,
                    parameterValues
            );
            findQueries.put(executableMethod, storedQuery);
        }


        Class<?> rootEntity = storedQuery.getRootEntity();
        Map<String, Object> parameterValues = buildParameterValues(context, storedQuery, rootEntity);

        Pageable pageable = getPageable(context);
        String query = storedQuery.getQuery();
        if (pageable != null) {
            Sort sort = pageable.getSort();
            if (sort.isSorted()) {
                QueryBuilder queryBuilder = getRequiredQueryBuilder(context);
                query += queryBuilder.buildOrderBy(PersistentEntity.of(rootEntity), sort).getQuery();
            }
        }
        return new DefaultPreparedQuery<>(
                storedQuery,
                query,
                parameterValues,
                pageable
        );
    }

    private <RT> Map<String, Object> buildParameterValues(MethodInvocationContext<T, R> context, StoredQuery<?, RT> storedQuery, Class<?> rootEntity) {
        Map<String, String> parameterBinding = storedQuery.getParameterBinding();
        Map<String, Object> parameterValueMap = context.getParameterValueMap();
        Map<String, Object> parameterValues = new HashMap<>(parameterBinding.size());
        for (Map.Entry<String, String> entry : parameterBinding.entrySet()) {
            String name = entry.getKey();
            String argument = entry.getValue();
            String v = storedQuery.getLastUpdatedProperty().orElse(null);
            if (parameterValueMap.containsKey(argument)) {
                parameterValues.put(name, parameterValueMap.get(argument));
            } else if (v != null && v.equals(argument)) {
                Class<?> lastUpdatedType = getLastUpdatedType(rootEntity, v);
                if (lastUpdatedType == null) {
                    throw new IllegalStateException("Could not establish last updated time for entity: " + rootEntity);
                }
                Object timestamp = ConversionService.SHARED.convert(OffsetDateTime.now(), lastUpdatedType).orElse(null);
                if (timestamp == null) {
                    throw new IllegalStateException("Unsupported date type: " + lastUpdatedType);
                }
                parameterValues.put(name, timestamp);
            } else {
                throw new IllegalArgumentException("Missing query arguments: " + argument);
            }

        }
        return parameterValues;
    }

    /**
     * Obtains the configured query builder.
     * @param context The context
     * @return The query builder
     */
    protected @NonNull QueryBuilder getRequiredQueryBuilder(@NonNull MethodInvocationContext<T, R> context) {
        return context.getValue(Repository.class, PredatorMethod.META_MEMBER_QUERY_BUILDER, QueryBuilder.class)
                .orElse(new JpaQueryBuilder());
    }

    /**
     * Prepares a query for the given context.
     * @param context The context
     * @return The query
     */
    protected final PreparedQuery<?, Number> prepareCountQuery(@NonNull MethodInvocationContext<T, R> context) {
        ExecutableMethod<T, R> executableMethod = context.getExecutableMethod();
        StoredQuery<?, Long> storedQuery = countQueries.get(executableMethod);
        if (storedQuery == null) {

            String query = context.stringValue(Query.class, PredatorMethod.META_MEMBER_COUNT_QUERY).orElseThrow(() ->
                    new IllegalStateException("No query present in method")
            );
            Class rootEntity = getRequiredRootEntity(context);

            Map<String, String> parameterBinding = Collections.emptyMap();

            if (context.isPresent(PredatorMethod.class, PredatorMethod.META_MEMBER_COUNT_PARAMETERS)) {
                parameterBinding = buildParameterBinding(
                        context,
                        PredatorMethod.META_MEMBER_COUNT_PARAMETERS
                );
            }
            storedQuery = new DefaultStoredQuery<Object, Long>(
                    executableMethod,
                    Long.class,
                    rootEntity,
                    query,
                    parameterBinding
            );
            countQueries.put(executableMethod, storedQuery);
        }

        Pageable pageable = getPageable(context);
        Map<String, Object> parameterValues = buildParameterValues(context, storedQuery, storedQuery.getRootEntity());
        //noinspection unchecked
        return new DefaultPreparedQuery(
                storedQuery,
                storedQuery.getQuery(),
                parameterValues,
                pageable
        ) {
            @Override
            public boolean isDtoProjection() {
                return false;
            }
        };
    }

    @NonNull
    private Map<String, String> buildParameterBinding(@NonNull MethodInvocationContext<T, R> context) {
        return buildParameterBinding(context, PredatorMethod.META_MEMBER_PARAMETER_BINDING);
    }

    /**
     * Builds the parameter data.
     * @param context The context
     * @param parameterBindingMember The parameter member
     * @return The parameter data
     */
    private Map<String, String> buildParameterBinding(
            @NonNull MethodInvocationContext<T, R> context,
            String parameterBindingMember) {
        AnnotationValue<PredatorMethod> annotation = context.getAnnotation(PredatorMethod.class);
        List<AnnotationValue<Property>> parameterData = annotation.getAnnotations(parameterBindingMember,
                Property.class);
        Map<String, String> parameterValues;
        if (CollectionUtils.isNotEmpty(parameterData)) {
            parameterValues = new HashMap<>(parameterData.size());
            for (AnnotationValue<Property> annotationValue : parameterData) {
                String name = annotationValue.get("name", String.class).orElse(null);
                String argument = annotationValue.get("value", String.class).orElse(null);
                if (name != null && argument != null) {
                    parameterValues.put(name, argument);
                }
            }
        } else {
            parameterValues = Collections.emptyMap();
        }
        return parameterValues;
    }

    private Class<?> getLastUpdatedType(Class<?> rootEntity, String property) {
        Class<?> type = lastUpdatedTypes.get(rootEntity);
        if (type == null) {
            type = BeanIntrospector.SHARED
                    .findIntrospection(rootEntity)
                    .flatMap(bp -> bp.getProperty(property))
                    .map(BeanProperty::getType).orElse(null);
            if (type != null) {
                lastUpdatedTypes.put(rootEntity, type);
            }
        }
        return type;
    }

    /**
     * Obtains the root entity or throws an exception if it not available.
     * @param context The context
     * @return The root entity type
     * @throws IllegalStateException If the root entity is unavailable
     */
    @NonNull
    protected Class<?> getRequiredRootEntity(MethodInvocationContext context) {
        return context.classValue(PredatorMethod.class, PredatorMethod.META_MEMBER_ROOT_ENTITY)
                .orElseThrow(() -> new IllegalStateException("No root entity present in method"));
    }

    /**
     * Retrieves a pageable from the context.
     * @param context The pageable
     * @return The pageable
     */
    protected @NonNull Pageable getRequiredPageable(MethodInvocationContext context) {
        Pageable pageable = getPageable(context);
        if (pageable == null) {
            throw new IllegalStateException("Pageable argument missing");
        }

        return pageable;
    }

    /**
     * Resolves the {@link Pageable} for the given context.
     * @param context The pageable
     * @return The pageable or null
     */
    @Nullable
    protected Pageable getPageable(MethodInvocationContext<?, ?> context) {
        String pageableParam = context.stringValue(PredatorMethod.class, TypeRole.PAGEABLE).orElse(null);
        Pageable pageable = null;
        Map<String, Object> parameterValueMap = context.getParameterValueMap();
        if (pageableParam != null) {
            pageable = ConversionService.SHARED
                    .convert(parameterValueMap.get(pageableParam), Pageable.class).orElse(null);

        } else {
            String sortParam = context.stringValue(PredatorMethod.class, TypeRole.SORT).orElse(null);
            if (sortParam != null) {
                Sort sort = ConversionService.SHARED.convert(parameterValueMap.get(sortParam), Sort.class).orElse(null);
                int max = context.intValue(PredatorMethod.class, PredatorMethod.META_MEMBER_PAGE_SIZE).orElse(-1);
                int pageIndex = context.intValue(PredatorMethod.class, PredatorMethod.META_MEMBER_PAGE_INDEX).orElse(0);
                boolean hasSize = max > 0;
                if (hasSize) {
                    if (sort != null) {
                        pageable = Pageable.from(pageIndex, max, sort);
                    } else {
                        pageable = Pageable.from(pageIndex, max);
                    }
                }
            }
        }
        return pageable;
    }

    /**
     * Return whether the metadata indicates the instance is nullable.
     * @param metadata The metadata
     * @return True if it is nullable
     */
    protected boolean isNullable(@NonNull AnnotationMetadata metadata) {
        return metadata
                .getDeclaredAnnotationNames()
                .stream()
                .anyMatch(n -> NameUtils.getSimpleName(n).equalsIgnoreCase("nullable"));
    }

    /**
     * Looks up the entity to persist from the execution context, or throws an exception.
     * @param context The context
     * @return The entity
     */
    protected @NonNull Object getRequiredEntity(MethodInvocationContext<T, Object> context) {
        String entityParam = context.stringValue(PredatorMethod.class, TypeRole.ENTITY)
                .orElseThrow(() -> new IllegalStateException("No entity parameter specified"));

        Object o = context.getParameterValueMap().get(entityParam);
        if (o == null) {
            throw new IllegalArgumentException("Entity argument cannot be null");
        }
        return o;
    }


    /**
     * Represents a prepared query.
     *
     * @param <E> The entity type
     * @param <RT> The result type
     */
    private final class DefaultStoredQuery<E, RT> implements StoredQuery<E, RT> {
        private final @NonNull Class<RT> resultType;
        private final @NonNull Class<E> rootEntity;
        private final @NonNull String query;
        private final @NonNull Map<String, String> parameterBinding;
        private final ExecutableMethod<?, ?> method;
        private final String lastUpdatedProp;

        /**
         * The default constructor.
         * @param method The target method
         * @param resultType The result type of the query
         * @param rootEntity The root entity of the query
         * @param query The query itself
         */
        DefaultStoredQuery(
                @NonNull ExecutableMethod<?, ?> method,
                @NonNull Class<RT> resultType,
                @NonNull Class<E> rootEntity,
                @NonNull String query,
                @Nullable Map<String, String> parameterBinding) {
            this.resultType = resultType;
            this.rootEntity = rootEntity;
            this.query = query;
            this.parameterBinding = parameterBinding == null ? Collections.emptyMap() : parameterBinding;
            this.method = method;
            this.lastUpdatedProp = method.stringValue(PredatorMethod.class, TypeRole.LAST_UPDATED_PROPERTY).orElse(null);
        }

        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return method.getAnnotationMetadata();
        }

        @Override
        public boolean isNative() {
            return method.isTrue(Query.class, "nativeQuery");
        }

        /**
         * @return Whether the query is a DTO query
         */
        @Override
        public boolean isDtoProjection() {
            return method.isTrue(PredatorMethod.class, PredatorMethod.META_MEMBER_DTO);
        }

        /**
         * @return The result type
         */
        @Override
        @NonNull
        public Class<RT> getResultType() {
            return resultType;
        }

        /**
         * @return The ID type
         */
        @Override
        @Nullable
        public Class<?> getEntityIdentifierType() {
            return method.classValue(PredatorMethod.class, PredatorMethod.META_MEMBER_ID_TYPE)
                    .orElse(null);
        }

        /**
         * @return The root entity type
         */
        @Override
        @NonNull
        public Class<E> getRootEntity() {
            return rootEntity;
        }

        /**
         * @return The query to execute
         */
        @Override
        @NonNull
        public String getQuery() {
            return query;
        }

        @Nonnull
        @Override
        public String getName() {
            return method.getMethodName();
        }

        @Override
        @NonNull
        public Class<?>[] getArgumentTypes() {
            return method.getArgumentTypes();
        }

        @NonNull
        @Override
        public Map<String, String> getParameterBinding() {
            return parameterBinding;
        }

        @Override
        public Optional<String> getLastUpdatedProperty() {
            return Optional.ofNullable(lastUpdatedProp);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            DefaultStoredQuery<?, ?> that = (DefaultStoredQuery<?, ?>) o;
            return resultType.equals(that.resultType) &&
                    method.equals(that.method);
        }

        @Override
        public int hashCode() {
            return Objects.hash(resultType, method);
        }
    }

    /**
     * Represents a prepared query.
     *
     * @param <E> The entity type
     * @param <RT> The result type
     */
    private class DefaultPreparedQuery<E, RT> implements PreparedQuery<E, RT> {
        private final @NonNull Map<String, Object> parameterValues;
        private final Pageable pageable;
        private final StoredQuery<E, RT> storedQuery;
        private final String query;

        /**
         * The default constructor.
         * @param storedQuery The stored query
         * @param finalQuery The final query
         * @param parameterValues The parameter values
         * @param pageable The pageable
         */
        DefaultPreparedQuery(
                StoredQuery<E, RT> storedQuery,
                String finalQuery,
                @Nullable Map<String, Object> parameterValues,
                @Nullable Pageable pageable) {
            this.query = finalQuery;
            this.storedQuery = storedQuery;
            this.parameterValues = parameterValues == null ? Collections.emptyMap() : parameterValues;
            this.pageable = pageable != null ? pageable : Pageable.UNPAGED;
        }

        @NonNull
        @Override
        public Map<String, Object> getParameterValues() {
            return parameterValues;
        }

        @NonNull
        @Override
        public Pageable getPageable() {
            return pageable;
        }

        @Override
        public boolean isNative() {
            return storedQuery.isNative();
        }

        @Override
        public boolean isDtoProjection() {
            return storedQuery.isDtoProjection();
        }

        @NonNull
        @Override
        public Class<RT> getResultType() {
            return storedQuery.getResultType();
        }

        @Nullable
        @Override
        public Class<?> getEntityIdentifierType() {
            return storedQuery.getEntityIdentifierType();
        }

        @NonNull
        @Override
        public Class<E> getRootEntity() {
            return storedQuery.getRootEntity();
        }

        @NonNull
        @Override
        public String getQuery() {
            return query;
        }

        @NonNull
        @Override
        public Class<?>[] getArgumentTypes() {
            return storedQuery.getArgumentTypes();
        }

        @NonNull
        @Override
        public Map<String, String> getParameterBinding() {
            return storedQuery.getParameterBinding();
        }

        @Override
        public Optional<String> getLastUpdatedProperty() {
            return storedQuery.getLastUpdatedProperty();
        }

        @Nonnull
        @Override
        public String getName() {
            return storedQuery.getName();
        }
    }


}
