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
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.intercept.PredatorInterceptor;
import io.micronaut.data.intercept.annotation.PredatorMethod;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.query.Sort;
import io.micronaut.data.runtime.datastore.Datastore;
import io.micronaut.data.runtime.query.PreparedQuery;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private static final String MEMBER_ROOT_MEMBER = "rootEntity";
    private static final String MEMBER_RESULT_TYPE = "resultType";
    private static final String MEMBER_ID_TYPE = "idType";
    private static final String MEMBER_PARAMETER_BINDING = "parameterBinding";
    protected final Datastore datastore;
    private final ConcurrentMap<Class, Class> lastUpdatedTypes = new ConcurrentHashMap<>(10);

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

        AnnotationValue<PredatorMethod> annotation = context.getAnnotation(PredatorMethod.class);

        if (annotation == null) {
            // this should never happen
            throw new IllegalStateException("No predator method configured");
        }
        Class rootEntity = annotation.get(MEMBER_ROOT_MEMBER, Class.class)
                .orElseThrow(() -> new IllegalStateException("No root entity present in method"));
        if (resultType == null) {
            resultType = annotation.get(MEMBER_RESULT_TYPE, Class.class).orElse(rootEntity);
        }
        Class idType = annotation.get(MEMBER_ID_TYPE, Class.class)
                .orElse(null);

        Map<String, Object> parameterValues = buildParameterBinding(context, annotation, rootEntity);

        Pageable pageable = getPageable(context);
        String query = context.getValue(Query.class, String.class).orElseThrow(() ->
                new IllegalStateException("No query present in method")
        );
        return new DefaultPreparedQuery(
                context.getAnnotationMetadata(),
                resultType,
                rootEntity,
                idType,
                query,
                parameterValues,
                pageable,
                context.isTrue(PredatorMethod.class, PredatorMethod.META_MEMBER_DTO)
        );
    }

    /**
     * Prepares a query for the given context.
     * @param context The context
     * @return The query
     */
    protected final PreparedQuery<?, Number> prepareCountQuery(MethodInvocationContext<T, R> context) {
        String query = context.getValue(Query.class, PredatorMethod.META_MEMBER_COUNT_QUERY, String.class).orElseThrow(() ->
                new IllegalStateException("No query present in method")
        );
        AnnotationValue<PredatorMethod> annotation = context.getAnnotation(PredatorMethod.class);
        if (annotation == null) {
            // this should never happen
            throw new IllegalStateException("No predator method configured");
        }
        Class rootEntity = annotation.get(MEMBER_ROOT_MEMBER, Class.class)
                .orElseThrow(() -> new IllegalStateException("No root entity present in method"));
        Class idType = annotation.get(MEMBER_ID_TYPE, Class.class)
                .orElse(null);

        @SuppressWarnings("ConstantConditions") Map<String, Object> parameterValues = Collections.emptyMap();

        AnnotationValue<Query> queryAnn = context.getAnnotation(Query.class);
        if (queryAnn != null) {
            if (queryAnn.contains(PredatorMethod.META_MEMBER_COUNT_PARAMETERS)) {

                parameterValues = buildParameterBinding(
                        context,
                        queryAnn,
                        PredatorMethod.META_MEMBER_COUNT_PARAMETERS,
                        rootEntity
                );
            } else {
                parameterValues = buildParameterBinding(
                        context,
                        annotation,
                        PredatorMethod.META_MEMBER_PARAMETER_BINDING,
                        rootEntity
                );
            }
        }

        Pageable pageable = getPageable(context);

        return new DefaultPreparedQuery(
                context.getAnnotationMetadata(),
                Long.class,
                rootEntity,
                idType,
                query,
                parameterValues,
                pageable,
                false
        );
    }

    @NonNull
    private Map<String, Object> buildParameterBinding(
            @NonNull MethodInvocationContext<T, R> context,
            @NonNull AnnotationValue<PredatorMethod> annotation,
            @NonNull Class<?> rootEntity) {
        return buildParameterBinding(context, annotation, MEMBER_PARAMETER_BINDING, rootEntity);
    }

    /**
     * Builds the parameter data.
     * @param context The context
     * @param annotation The predator annotation
     * @param parameterBindingMember The member that holds the parameter binding
     * @param rootEntity The root entity
     * @return The parameter data
     */
    private Map<String, Object> buildParameterBinding(
            @NonNull MethodInvocationContext<T, R> context,
            @NonNull AnnotationValue<?> annotation,
            String parameterBindingMember,
            @NonNull Class<?> rootEntity) {
        List<AnnotationValue<Property>> parameterData = annotation.getAnnotations(parameterBindingMember,
                Property.class);
        Map<String, Object> parameterValues;
        Map<String, Object> parameterValueMap = context.getParameterValueMap();
        if (CollectionUtils.isNotEmpty(parameterData)) {
            parameterValues = new HashMap<>(parameterData.size());
            for (AnnotationValue<Property> annotationValue : parameterData) {
                String name = annotationValue.get("name", String.class).orElse(null);
                String argument = annotationValue.get("value", String.class).orElse(null);
                if (name != null && argument != null) {
                    if (parameterValueMap.containsKey(argument)) {
                        parameterValues.put(name, parameterValueMap.get(argument));
                    } else {
                        String v = context.getValue(PredatorMethod.class, TypeRole.LAST_UPDATED_PROPERTY, String.class).orElse(null);
                        if (v != null && v.equals(argument)) {
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
        return context.getValue(PredatorMethod.class, MEMBER_ROOT_MEMBER, Class.class)
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
    protected Pageable getPageable(MethodInvocationContext context) {
        String pageableParam = context.getValue(PredatorMethod.class, TypeRole.PAGEABLE, String.class).orElse(null);
        Pageable pageable = null;
        if (pageableParam != null) {
            Map<String, Object> parameterValueMap = context.getParameterValueMap();
            pageable = ConversionService.SHARED
                    .convert(parameterValueMap.get(pageableParam), Pageable.class).orElse(null);

        } else {
            Sort sortParam = context.getValue(PredatorMethod.class, TypeRole.SORT, Sort.class).orElse(null);
            int max = context.getValue(PredatorMethod.class, PredatorMethod.META_MEMBER_PAGE_SIZE, int.class).orElse(-1);
            int pageIndex = context.getValue(PredatorMethod.class, PredatorMethod.META_MEMBER_PAGE_INDEX, int.class).orElse(0);
            boolean hasSize = max > 0;
            if (hasSize) {
                if (sortParam != null) {
                    pageable = Pageable.from(pageIndex, max, sortParam);
                } else {
                    pageable = Pageable.from(pageIndex, max);
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
        String entityParam = context.getValue(PredatorMethod.class, TypeRole.ENTITY, String.class)
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
    private final class DefaultPreparedQuery<E, RT> implements PreparedQuery<E, RT> {
        private final @NonNull Class resultType;
        private final @NonNull Class rootEntity;
        private final @Nullable Class idType;
        private final @NonNull String query;
        private final @NonNull Map<String, Object> parameterValues;
        private final Pageable pageable;
        private final boolean dto;
        private final AnnotationMetadata annotationMetadata;

        /**
         * The default constructor.
         * @param annotationMetadata The annotation metadata
         * @param resultType The result type of the query
         * @param rootEntity The root entity of the query
         * @param idType The ID type
         * @param query The query itself
         * @param parameterValues The parameter values
         * @param pageable The pageable
         * @param dto Is the query a DTO query
         */
        DefaultPreparedQuery(
                @NonNull AnnotationMetadata annotationMetadata,
                @NonNull Class resultType,
                @NonNull Class rootEntity,
                @Nullable Class<?> idType,
                @NonNull String query,
                @Nullable Map<String, Object> parameterValues,
                @Nullable Pageable pageable,
                boolean dto) {
            this.resultType = resultType;
            this.rootEntity = rootEntity;
            this.idType = idType;
            this.query = query;
            this.parameterValues = parameterValues == null ? Collections.emptyMap() : parameterValues;
            this.pageable = pageable;
            this.dto = dto;
            this.annotationMetadata = annotationMetadata;
        }

        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return annotationMetadata;
        }

        /**
         * @return Whether the query is a DTO query
         */
        @Override
        public boolean isDtoProjection() {
            return dto;
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
            return idType;
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

        /**
         * @return The parameter values to bind to the query
         */
        @Override
        @NonNull
        public Map<String, Object> getParameterValues() {
            return parameterValues;
        }

        /**
         * @return The pageable
         */
        @Override
        @NonNull
        public Pageable getPageable() {
            return pageable != null ? pageable : Pageable.unpaged();
        }
    }
}
