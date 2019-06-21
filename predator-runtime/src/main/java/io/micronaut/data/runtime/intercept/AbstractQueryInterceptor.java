/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.data.runtime.intercept;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.BeanWrapper;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.*;
import io.micronaut.data.intercept.PredatorInterceptor;
import io.micronaut.data.intercept.annotation.PredatorMethod;
import io.micronaut.data.model.*;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.runtime.*;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.jpa.JpaQueryBuilder;
import io.micronaut.inject.ExecutableMethod;

import javax.annotation.Nonnull;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Abstract interceptor that executes a {@link io.micronaut.data.annotation.Query}.
 * @param <T> The declaring type
 * @param <R> The return type
 * @since 1.0
 * @author graemerocher
 */
public abstract class AbstractQueryInterceptor<T, R> implements PredatorInterceptor<T, R> {
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("(:[a-zA-Z0-9]+)");
    protected final RepositoryOperations operations;
    private final ConcurrentMap<Class, Class> lastUpdatedTypes = new ConcurrentHashMap<>(10);
    private final ConcurrentMap<MethodKey, StoredQuery> findQueries = new ConcurrentHashMap<>(50);
    private final ConcurrentMap<MethodKey, StoredQuery> countQueries = new ConcurrentHashMap<>(50);

    /**
     * Default constructor.
     * @param operations The operations
     */
    protected AbstractQueryInterceptor(@NonNull RepositoryOperations operations) {
        ArgumentUtils.requireNonNull("operations", operations);
        this.operations = operations;
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
        Class<?> repositoryType = context.getTarget().getClass();
        MethodKey key = newMethodKey(repositoryType, executableMethod);
        StoredQuery<?, RT> storedQuery = findQueries.get(key);
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
            storedQuery = new DefaultStoredQuery<>(
                    executableMethod,
                    resultType,
                    rootEntity,
                    query,
                    PredatorMethod.META_MEMBER_PARAMETER_BINDING
            );
            findQueries.put(key, storedQuery);
        }


        Class<?> rootEntity = storedQuery.getRootEntity();
        Map<String, Object> parameterValues = buildParameterValues(context, storedQuery, rootEntity);

        Pageable pageable = getPageable(context);
        String query = storedQuery.getQuery();
        return new DefaultPreparedQuery<>(
                repositoryType,
                storedQuery,
                query,
                parameterValues,
                pageable
        );
    }

    @NonNull
    private MethodKey newMethodKey(Class<?> type, ExecutableMethod<T, R> executableMethod) {
        return new MethodKey(type, executableMethod.getMethodName(), executableMethod.getArgumentTypes());
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
        Class<?> repositoryType = context.getTarget().getClass();
        MethodKey key = newMethodKey(repositoryType, executableMethod);
        StoredQuery<?, Long> storedQuery = countQueries.get(key);
        if (storedQuery == null) {

            String query = context.stringValue(Query.class, PredatorMethod.META_MEMBER_COUNT_QUERY).orElseThrow(() ->
                    new IllegalStateException("No query present in method")
            );
            Class rootEntity = getRequiredRootEntity(context);

            storedQuery = new DefaultStoredQuery<Object, Long>(
                    executableMethod,
                    Long.class,
                    rootEntity,
                    query,
                    context.isPresent(PredatorMethod.class, PredatorMethod.META_MEMBER_COUNT_PARAMETERS) ? PredatorMethod.META_MEMBER_COUNT_PARAMETERS : null
            );
            countQueries.put(key, storedQuery);
        }

        Pageable pageable = getPageable(context);
        Map<String, Object> parameterValues = buildParameterValues(context, storedQuery, storedQuery.getRootEntity());
        //noinspection unchecked
        return new DefaultPreparedQuery(
                repositoryType,
                storedQuery,
                storedQuery.getQuery(),
                parameterValues,
                pageable,
                false
        );
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
            Object o = parameterValueMap.get(pageableParam);
            if (o instanceof Pageable) {
                pageable = (Pageable) o;
            } else {
                pageable = ConversionService.SHARED
                        .convert(o, Pageable.class).orElse(null);
            }
        } else {
            String sortParam = context.stringValue(PredatorMethod.class, TypeRole.SORT).orElse(null);
            if (sortParam != null) {
                Object o = parameterValueMap.get(sortParam);
                Sort sort;
                if (o instanceof Sort) {
                    sort = (Sort) o;
                } else {
                    sort = ConversionService.SHARED.convert(o, Sort.class).orElse(null);
                }

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
            } else {
                int max = context.intValue(PredatorMethod.class, PredatorMethod.META_MEMBER_PAGE_SIZE).orElse(-1);
                if (max > -1) {
                    return Pageable.from(0, max);
                }
            }
        }
        return pageable != null ? pageable : Pageable.UNPAGED;
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
    protected @NonNull Object getRequiredEntity(MethodInvocationContext<T, ?> context) {
        String entityParam = context.stringValue(PredatorMethod.class, TypeRole.ENTITY)
                .orElseThrow(() -> new IllegalStateException("No entity parameter specified"));

        Object o = context.getParameterValueMap().get(entityParam);
        if (o == null) {
            throw new IllegalArgumentException("Entity argument cannot be null");
        }
        return o;
    }

    @SuppressWarnings("unchecked")
    private <RT> Map buildParameterValues(MethodInvocationContext<T, R> context, StoredQuery<?, RT> storedQuery, Class<?> rootEntity) {
        Map<?, ?> parameterBinding = storedQuery.useNumericPlaceholders() ? storedQuery.getIndexedParameterBinding() : storedQuery.getParameterBinding();
        Map<String, Object> parameterValueMap = context.getParameterValueMap();
        Map parameterValues = new HashMap<>(parameterBinding.size());
        for (Map.Entry entry : parameterBinding.entrySet()) {
            Object name = entry.getKey();
            String argument = (String) entry.getValue();
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
                Optional<Argument> named = Arrays.stream(context.getArguments())
                        .filter(arg -> {
                            String n = arg.getAnnotationMetadata().stringValue(Parameter.class).orElse(arg.getName());
                            return n.equals(argument);
                        })
                        .findFirst();
                if (named.isPresent()) {
                    parameterValues.put(name, parameterValueMap.get(named.get().getName()));
                } else {
                    throw new IllegalArgumentException("Missing query arguments: " + argument);
                }
            }

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
     * Instantiate the given entity for the given parameter values.
     *
     * @param rootEntity The entity
     * @param parameterValues The parameter values
     * @return The entity
     * @throws IllegalArgumentException if the entity cannot be instantiated due to an illegal argument
     */
    protected @NonNull Object instantiateEntity(@NonNull Class<?> rootEntity, @NonNull Map<String, Object> parameterValues) {
        PersistentEntity entity = PersistentEntity.of(rootEntity);
        BeanIntrospection<?> introspection = BeanIntrospection.getIntrospection(rootEntity);
        Argument<?>[] constructorArguments = introspection.getConstructorArguments();
        Object instance;
        if (ArrayUtils.isNotEmpty(constructorArguments)) {

            Object[] arguments = new Object[constructorArguments.length];
            for (int i = 0; i < constructorArguments.length; i++) {
                Argument<?> argument = constructorArguments[i];

                String argumentName = argument.getName();
                Object v = parameterValues.get(argumentName);
                AnnotationMetadata argMetadata = argument.getAnnotationMetadata();
                if (v == null && !PersistentProperty.isNullableMetadata(argMetadata)) {
                    PersistentProperty prop = entity.getPropertyByName(argumentName);
                    if (prop == null || prop.isRequired()) {
                        throw new IllegalArgumentException("Argument [" + argumentName + "] cannot be null");
                    }
                }
                arguments[i] = v;
            }
            instance = introspection.instantiate(arguments);
        } else {
            instance = introspection.instantiate();
        }

        BeanWrapper<Object> wrapper = BeanWrapper.getWrapper(instance);
        List<PersistentProperty> persistentProperties = entity.getPersistentProperties();
        for (PersistentProperty prop : persistentProperties) {
            if (!prop.isReadOnly() && !prop.isGenerated()) {
                String propName = prop.getName();
                Object v = parameterValues.get(propName);
                if (v == null && !prop.isOptional()) {
                    throw new IllegalArgumentException("Argument [" + propName + "] cannot be null");
                }
                wrapper.setProperty(propName, v);
            }
        }
        return instance;
    }

    /**
     * Convert a number argument if necessary.
     * @param number The number
     * @param argument The argument
     * @return The result
     */
    protected @Nullable Number convertNumberArgumentIfNecessary(Number number, Argument<?> argument) {
        Argument<?> firstTypeVar = argument.getFirstTypeVariable().orElse(Argument.of(Long.class));
        Class<?> type = firstTypeVar.getType();
        if (type == Object.class || type == Void.class) {
            return null;
        }
        if (number == null) {
            number = 0;
        }
        if (!type.isInstance(number)) {
            return (Number) ConversionService.SHARED.convert(number, firstTypeVar)
                    .orElseThrow(() -> new IllegalStateException("Unsupported number type for return type: " + firstTypeVar));
        } else {
            return number;
        }
    }

    /**
     * Get the paged query for the given context.
     * @param context The contet
     * @param <E> The entity type
     * @return The paged query
     */
    protected @NonNull <E> PagedQuery<E> getPagedQuery(@NonNull MethodInvocationContext context) {
        @SuppressWarnings("unchecked") Class<E> rootEntity = (Class<E>) getRequiredRootEntity(context);
        Pageable pageable = getPageable(context);

        return new DefaultPagedQuery<>(context.getExecutableMethod(), rootEntity, pageable);
    }

    /**
     * Get the batch oepration for the given context.
     * @param context The context
     * @param iterable The iterable
     * @param <E> The entity type
     * @return The paged query
     */
    protected @NonNull <E> BatchOperation<E> getBatchOperation(@NonNull MethodInvocationContext context, @NonNull Iterable<E> iterable) {
        @SuppressWarnings("unchecked") Class<E> rootEntity = (Class<E>) getRequiredRootEntity(context);
        return getBatchOperation(context, rootEntity, iterable);
    }

    /**
     * Get the batch operation for the given context.
     * @param <E> The entity type
     * @param context The context
     * @param rootEntity The root entity
     * @param iterable The iterable
     * @return The paged query
     */
    protected <E> BatchOperation<E> getBatchOperation(@NonNull MethodInvocationContext context, Class<E> rootEntity, @NonNull Iterable<E> iterable) {
        return new DefaultBatchOperation<>(context.getExecutableMethod(), rootEntity, iterable);
    }

    /**
     * Get the batch operation for the given context.
     * @param context The context
     * @param <E> The entity type
     * @return The paged query
     */
    protected @NonNull <E> BatchOperation<E> getBatchOperation(@NonNull MethodInvocationContext context) {
        @SuppressWarnings("unchecked") Class<E> rootEntity = (Class<E>) getRequiredRootEntity(context);
        return getBatchOperation(context, rootEntity);
    }

    /**
     * Get the batch operation for the given context.
     * @param context The context
     * @param rootEntity The root entity
     * @param <E> The entity type
     * @return The paged query
     */
    protected <E> BatchOperation<E> getBatchOperation(@NonNull MethodInvocationContext context, @NonNull Class<E> rootEntity) {
        return new AllBatchOperation<>(context.getExecutableMethod(), rootEntity);
    }

    /**
     * Get the batch operation for the given context.
     * @param context The context
     * @param <E> The entity type
     * @return The paged query
     */
    @SuppressWarnings("unchecked")
    protected <E> InsertOperation<E> getInsertOperation(@NonNull MethodInvocationContext context) {
        E o = (E) getRequiredEntity(context);
        return new DefaultInsertOperation<>(context.getExecutableMethod(), o);
    }

    /**
     * Get the batch operation for the given context.
     * @param context The context
     * @param entity The entity
     * @param <E> The entity type
     * @return The paged query
     */
    protected <E> InsertOperation<E> getInsertOperation(@NonNull MethodInvocationContext context, E entity) {
        return new DefaultInsertOperation<>(context.getExecutableMethod(), entity);
    }

    /**
     * Default implementation of {@link InsertOperation}.
     * @param <E> The entity type
     */
    private final class DefaultInsertOperation<E> implements InsertOperation<E> {
        private final ExecutableMethod<?, ?> method;
        private final E entity;

        DefaultInsertOperation(ExecutableMethod<?, ?> method, E entity) {
            this.method = method;
            this.entity = entity;
        }

        @NonNull
        @Override
        public Class<E> getRootEntity() {
            return (Class<E>) entity.getClass();
        }

        @Override
        public E getEntity() {
            return entity;
        }

        @Nonnull
        @Override
        public String getName() {
            return method.getMethodName();
        }

        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return method.getAnnotationMetadata();
        }
    }

    /**
     * Default implementation of {@link BatchOperation}.
     * @param <E> The entity type
     */
    private final class DefaultBatchOperation<E> implements BatchOperation<E> {
        private final ExecutableMethod<?, ?> method;
        private final @NonNull Class<E> rootEntity;
        private final Iterable<E> iterable;

        public DefaultBatchOperation(ExecutableMethod<?, ?> method, @NonNull Class<E> rootEntity, Iterable<E> iterable) {
            this.method = method;
            this.rootEntity = rootEntity;
            this.iterable = iterable;
        }

        @NonNull
        @Override
        public Class<E> getRootEntity() {
            return rootEntity;
        }

        @Nonnull
        @Override
        public String getName() {
            return method.getMethodName();
        }

        @Override
        public Iterator<E> iterator() {
            return iterable.iterator();
        }

        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return method.getAnnotationMetadata();
        }
    }

    /**
     * Default implementation of {@link BatchOperation}.
     *
     * @param <E> The entity type
     */
    private final class AllBatchOperation<E> implements BatchOperation<E> {
        private final ExecutableMethod<?, ?> method;
        private final @NonNull Class<E> rootEntity;

        public AllBatchOperation(ExecutableMethod<?, ?> method, @NonNull Class<E> rootEntity) {
            this.method = method;
            this.rootEntity = rootEntity;
        }

        @Override
        public boolean all() {
            return true;
        }

        @NonNull
        @Override
        public Class<E> getRootEntity() {
            return rootEntity;
        }

        @Nonnull
        @Override
        public String getName() {
            return method.getMethodName();
        }

        @Override
        public Iterator<E> iterator() {
            return Collections.emptyIterator();
        }

        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return method.getAnnotationMetadata();
        }
    }

    /**
     * Default implementation of {@link PagedQuery}.
     *
     * @param <E> The paged query
     */
    private final class DefaultPagedQuery<E> implements PagedQuery<E> {

        private final ExecutableMethod<?, ?> method;
        private final @NonNull Class<E> rootEntity;
        private final Pageable pageable;

        /**
         * Default constructor.
         * @param method The method
         * @param rootEntity The root entity
         * @param pageable The pageable
         */
        DefaultPagedQuery(ExecutableMethod<?, ?> method, @NonNull Class<E> rootEntity, Pageable pageable) {
            this.method = method;
            this.rootEntity = rootEntity;
            this.pageable = pageable;
        }

        @NonNull
        @Override
        public Class<E> getRootEntity() {
            return rootEntity;
        }

        @NonNull
        @Override
        public Pageable getPageable() {
            return pageable;
        }

        @Nonnull
        @Override
        public String getName() {
            return method.getMethodName();
        }

        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return method.getAnnotationMetadata();
        }
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
        private final @Nullable Map<String, String> parameterBinding;
        private final @Nullable Map<Integer, String> indexedParameterBinding;
        private final ExecutableMethod<?, ?> method;
        private final String lastUpdatedProp;
        private final boolean isDto;
        private final boolean isNative;
        private final boolean isNumericPlaceHolder;
        private final AnnotationMetadata annotationMetadata;
        private final boolean hasIn;
        private final boolean isCount;
        private final Map<String, DataType> dataTypes;
        private final Map<Integer, DataType> indexedDataTypes;
        private Map<String, Object> queryHints;

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
                @Nullable String parameterBindingMember) {
            this.resultType = resultType;
            this.rootEntity = rootEntity;
            this.annotationMetadata = method.getAnnotationMetadata();
            this.isNative = method.isTrue(Query.class, "nativeQuery");
            this.isNumericPlaceHolder = method.classValue(Repository.class, "queryBuilder").map(c -> c == SqlQueryBuilder.class).orElse(false);
            this.hasIn = isNumericPlaceHolder && query.contains(SqlQueryBuilder.IN_EXPRESSION_START);

            if (isNumericPlaceHolder && method.isTrue(Query.class, PredatorMethod.META_MEMBER_RAW_QUERY)) {
                Matcher matcher = VARIABLE_PATTERN.matcher(query);
                this.query = matcher.replaceAll("?");
            } else {
                this.query = query;
            }
            this.method = method;
            this.lastUpdatedProp = method.stringValue(PredatorMethod.class, TypeRole.LAST_UPDATED_PROPERTY).orElse(null);
            this.isDto = method.isTrue(PredatorMethod.class, PredatorMethod.META_MEMBER_DTO);

            this.isCount = parameterBindingMember != null && parameterBindingMember.startsWith("count");
            AnnotationValue<PredatorMethod> annotation = annotationMetadata.getAnnotation(PredatorMethod.class);
            if (parameterBindingMember != null && annotation != null) {

                    List<AnnotationValue<Property>> parameterData = annotation.getAnnotations(parameterBindingMember,
                            Property.class);
                    if (CollectionUtils.isNotEmpty(parameterData)) {
                        Map parameterValues = new HashMap(parameterData.size());
                        for (AnnotationValue<Property> annotationValue : parameterData) {
                            Object placeHolderName;
                            if (isNumericPlaceHolder) {
                                int i = annotationValue.intValue("name").orElse(-1);
                                if (i == -1) {
                                    continue;
                                }
                                placeHolderName = i;
                            } else {
                                placeHolderName = annotationValue.stringValue("name").orElse(null);
                            }
                            String argument = annotationValue.stringValue("value").orElse(null);
                            if (placeHolderName != null && argument != null) {
                                if (isNumericPlaceHolder) {
                                    parameterValues.put(placeHolderName, argument);
                                } else {
                                    parameterValues.put(placeHolderName, argument);
                                }
                            }
                        }
                        if (isNumericPlaceHolder) {
                            this.indexedParameterBinding = parameterValues;
                            this.parameterBinding = null;
                        } else {
                            this.parameterBinding = parameterValues;
                            this.indexedParameterBinding = null;
                        }
                    } else {
                        this.parameterBinding = null;
                        this.indexedParameterBinding = null;
                    }
            } else {
                this.indexedParameterBinding = null;
                this.parameterBinding = null;
            }
            if (method.hasAnnotation(QueryHint.class)) {
                List<AnnotationValue<QueryHint>> values = method.getAnnotationValuesByType(QueryHint.class);
                this.queryHints = new HashMap<>(values.size());
                for (AnnotationValue<QueryHint> value : values) {
                    String n = value.stringValue("name").orElse(null);
                    String v = value.stringValue("value").orElse(null);
                    if (StringUtils.isNotEmpty(n) && StringUtils.isNotEmpty(v)) {
                        queryHints.put(n, v);
                    }
                }
            }
            Map<String, Object> queryHints = operations.getQueryHints(this);
            if (queryHints != Collections.EMPTY_MAP) {
                if (this.queryHints != null) {
                    this.queryHints.putAll(queryHints);
                } else {
                    this.queryHints = queryHints;
                }
            }

            List<AnnotationValue<TypeDef>> typeDefs = annotation != null ? annotation.getAnnotations("typeDefs", TypeDef.class) : null;
            if (CollectionUtils.isNotEmpty(typeDefs)) {
                this.dataTypes = isNumericPlaceHolder ? null : new HashMap<>(typeDefs.size());
                this.indexedDataTypes = isNumericPlaceHolder ? new HashMap<>(typeDefs.size()) : null;
                for (AnnotationValue<TypeDef> typeDef : typeDefs) {
                    typeDef.enumValue("type", DataType.class).ifPresent(dataType -> {
                        String[] values = typeDef.stringValues("names");
                        for (String value : values) {
                            if (isNumericPlaceHolder) {
                                indexedDataTypes.put(Integer.valueOf(value), dataType);
                            } else {
                                dataTypes.put(value, dataType);
                            }
                        }
                    });
                }
            } else {
                this.indexedDataTypes = null;
                this.dataTypes = null;
            }
        }

        @Override
        public boolean isCount() {
            return isCount;
        }

        @NonNull
        @Override
        public Map<String, DataType> getParameterTypes() {
            if (dataTypes == null) {
                return Collections.emptyMap();
            }
            return this.dataTypes;
        }

        @NonNull
        @Override
        public Map<Integer, DataType> getIndexedParameterTypes() {
            if (indexedDataTypes == null) {
                return Collections.emptyMap();
            }
            return this.indexedDataTypes;
        }

        @NonNull
        @Override
        public Map<String, Object> getQueryHints() {
            if (queryHints != null) {
                return queryHints;
            }
            return Collections.emptyMap();
        }

        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return annotationMetadata;
        }

        @Override
        public boolean isNative() {
            return isNative;
        }

        /**
         * Is this a raw SQL query.
         * @return The raw sql query.
         */
        @Override
        public boolean useNumericPlaceholders() {
            return isNumericPlaceHolder;
        }

        /**
         * @return Whether the query is a DTO query
         */
        @Override
        public boolean isDtoProjection() {
            return isDto;
        }

        /**
         * @return The result type
         */
        @Override
        @NonNull
        public Class<RT> getResultType() {
            return resultType;
        }

        @NonNull
        @Override
        public DataType getResultDataType() {
            return annotationMetadata.findAnnotation(PredatorMethod.class)
                                     .flatMap(av -> av.enumValue(PredatorMethod.META_MEMBER_RESULT_DATA_TYPE, DataType.class))
                                     .orElse(DataType.OBJECT);
        }

        /**
         * @return The ID type
         */
        @SuppressWarnings("unchecked")
        @Override
        public Optional<Class<?>> getEntityIdentifierType() {
            Optional o = annotationMetadata.classValue(PredatorMethod.class, PredatorMethod.META_MEMBER_ID_TYPE);
            return o;
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
         * Does the query contain an in expression.
         * @return True if it does
         */
        @Override
        public boolean hasInExpression() {
            return hasIn;
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
            if (parameterBinding == null) {
                return Collections.emptyMap();
            }
            return parameterBinding;
        }

        @NonNull
        @Override
        public Map<Integer, String> getIndexedParameterBinding() {
            if (indexedParameterBinding == null) {
                return Collections.emptyMap();
            }
            return indexedParameterBinding;
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
    private final class DefaultPreparedQuery<E, RT> implements PreparedQuery<E, RT> {
        private final @NonNull Map<String, Object> parameterValues;
        private final Pageable pageable;
        private final StoredQuery<E, RT> storedQuery;
        private final String query;
        private final boolean dto;
        private final @NonNull Map<Integer, Object> indexedValues;
        private final Class<?> repositoryType;

        /**
         * The default constructor.
         * @param repositoryType The repository type
         * @param storedQuery The stored query
         * @param finalQuery The final query
         * @param parameterValues The parameter values
         * @param pageable The pageable
         */
        DefaultPreparedQuery(
                Class<?> repositoryType,
                StoredQuery<E, RT> storedQuery,
                String finalQuery,
                @Nullable Map<String, Object> parameterValues,
                @Nullable Pageable pageable) {
            this(repositoryType, storedQuery, finalQuery, parameterValues, pageable, storedQuery.isDtoProjection());
        }

        /**
         * The default constructor.
         * @param repositoryType The repository type
         * @param storedQuery The stored query
         * @param finalQuery The final query
         * @param parameterValues The parameter values
         * @param pageable The pageable
         * @param dtoProjection Whether the prepared query is a dto projection
         */
        DefaultPreparedQuery(
                Class<?> repositoryType,
                StoredQuery<E, RT> storedQuery,
                String finalQuery,
                @Nullable Map parameterValues,
                @Nullable Pageable pageable,
                boolean dtoProjection) {
            this.repositoryType = repositoryType;
            this.query = finalQuery;
            this.storedQuery = storedQuery;
            if (storedQuery.useNumericPlaceholders()) {
                if (parameterValues != null) {
                    indexedValues = parameterValues;
                } else {
                    indexedValues = Collections.emptyMap();
                }
                this.parameterValues = Collections.emptyMap();
            } else {
                this.indexedValues = Collections.emptyMap();
                this.parameterValues = parameterValues == null ? Collections.emptyMap() : parameterValues;
            }

            this.pageable = pageable != null ? pageable : Pageable.UNPAGED;
            this.dto = dtoProjection;
        }

        @NonNull
        @Override
        public Map<Integer, DataType> getIndexedParameterTypes() {
            return storedQuery.getIndexedParameterTypes();
        }

        @NonNull
        @Override
        public Map<Integer, String> getIndexedParameterBinding() {
            return storedQuery.getIndexedParameterBinding();
        }

        @NonNull
        @Override
        public Map<String, DataType> getParameterTypes() {
            return storedQuery.getParameterTypes();
        }

        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return storedQuery.getAnnotationMetadata();
        }

        @NonNull
        @Override
        public Map<String, Object> getQueryHints() {
            return storedQuery.getQueryHints();
        }

        @Override
        public Class<?> getRepositoryType() {
            return repositoryType;
        }

        @NonNull
        @Override
        public Map<String, Object> getParameterValues() {
            return parameterValues;
        }

        @Override
        @NonNull
        public Map<Integer, Object> getIndexedParameterValues() {
            return indexedValues;
        }

        @NonNull
        @Override
        public Pageable getPageable() {
            if (storedQuery.isCount()) {
                return Pageable.UNPAGED;
            } else {
                return pageable;
            }
        }

        @Override
        public boolean isNative() {
            return storedQuery.isNative();
        }

        @Override
        public boolean useNumericPlaceholders() {
            return storedQuery.useNumericPlaceholders();
        }

        @Override
        public boolean isDtoProjection() {
            return dto;
        }

        @NonNull
        @Override
        public Class<RT> getResultType() {
            return storedQuery.getResultType();
        }

        @NonNull
        @Override
        public DataType getResultDataType() {
            return storedQuery.getResultDataType();
        }

        @Nullable
        @Override
        public Optional<Class<?>> getEntityIdentifierType() {
            return storedQuery.getEntityIdentifierType();
        }

        @NonNull
        @Override
        public Class<E> getRootEntity() {
            return storedQuery.getRootEntity();
        }

        @Override
        public boolean hasInExpression() {
            return storedQuery.hasInExpression();
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
        public boolean isCount() {
            return storedQuery.isCount();
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

    /**
     * Class used as a method key.
     */
    private final class MethodKey {
        final Class declaringType;
        final String name;
        final Class[] argumentTypes;

        MethodKey(Class declaringType, String name, Class[] argumentTypes) {
            this.declaringType = declaringType;
            this.name = name;
            this.argumentTypes = argumentTypes;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            MethodKey methodKey = (MethodKey) o;
            return declaringType.equals(methodKey.declaringType) &&
                    name.equals(methodKey.name) &&
                    Arrays.equals(argumentTypes, methodKey.argumentTypes);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(declaringType, name);
            result = 31 * result + Arrays.hashCode(argumentTypes);
            return result;
        }
    }
}
