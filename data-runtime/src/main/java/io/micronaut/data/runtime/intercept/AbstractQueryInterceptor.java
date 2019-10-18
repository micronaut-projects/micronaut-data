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
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.BeanWrapper;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableArgumentValue;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.*;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.intercept.DataInterceptor;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.*;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.runtime.*;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.inject.ExecutableMethod;

import javax.annotation.Nonnull;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import static io.micronaut.data.intercept.annotation.DataMethod.META_MEMBER_PAGE_SIZE;
import static io.micronaut.data.model.query.builder.QueryBuilder.VARIABLE_PATTERN;

/**
 * Abstract interceptor that executes a {@link io.micronaut.data.annotation.Query}.
 * @param <T> The declaring type
 * @param <R> The return type
 * @since 1.0
 * @author graemerocher
 */
public abstract class AbstractQueryInterceptor<T, R> implements DataInterceptor<T, R> {
    private static final String PREDATOR_ANN_NAME = DataMethod.class.getName();
    private static final int[] EMPTY_INT_ARRAY = new int[0];
    protected final RepositoryOperations operations;
    private final ConcurrentMap<Class, Class> lastUpdatedTypes = new ConcurrentHashMap<>(10);
    private final ConcurrentMap<RepositoryMethodKey, StoredQuery> findQueries = new ConcurrentHashMap<>(50);
    private final ConcurrentMap<RepositoryMethodKey, StoredQuery> countQueries = new ConcurrentHashMap<>(50);

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
     *
     * @param key The method key
     * @param context The context
     * @return The query
     */
    protected final PreparedQuery<?, ?> prepareQuery(RepositoryMethodKey key, MethodInvocationContext<T, R> context) {
        return prepareQuery(key, context, null);
    }

    /**
     * Prepares a query for the given context.
     *
     * @param <RT> The result generic type
     * @param methodKey The method key
     * @param context The context
     * @param resultType The result type
     * @return The query
     */
    protected final <RT> PreparedQuery<?, RT> prepareQuery(
            RepositoryMethodKey methodKey,
            MethodInvocationContext<T, R> context,
            Class<RT> resultType) {
        validateNullArguments(context);
        StoredQuery<?, RT> storedQuery = findQueries.get(methodKey);
        if (storedQuery == null) {
            Class<?> rootEntity = context.classValue(PREDATOR_ANN_NAME, DataMethod.META_MEMBER_ROOT_ENTITY)
                    .orElseThrow(() -> new IllegalStateException("No root entity present in method"));
            if (resultType == null) {
                //noinspection unchecked
                resultType = (Class<RT>) context.classValue(PREDATOR_ANN_NAME, DataMethod.META_MEMBER_RESULT_TYPE)
                        .orElse(rootEntity);
            }
            String query = context.stringValue(Query.class).orElseThrow(() ->
                    new IllegalStateException("No query present in method")
            );
            storedQuery = new DefaultStoredQuery<>(
                    context.getExecutableMethod(),
                    resultType,
                    rootEntity,
                    query,
                    DataMethod.META_MEMBER_PARAMETER_BINDING,
                    false
            );
            findQueries.put(methodKey, storedQuery);
        }



        Pageable pageable = storedQuery.hasPageable() ? getPageable(context) : Pageable.UNPAGED;
        String query = storedQuery.getQuery();
        return new DefaultPreparedQuery<>(
                context,
                storedQuery,
                query,
                pageable,
                storedQuery.isDtoProjection()
        );
    }

    /**
     * Prepares a query for the given context.
     *
     * @param methodKey The method key
     * @param context The context
     * @return The query
     */
    protected final PreparedQuery<?, Number> prepareCountQuery(RepositoryMethodKey methodKey, @NonNull MethodInvocationContext<T, R> context) {
        ExecutableMethod<T, R> executableMethod = context.getExecutableMethod();
        StoredQuery<?, Long> storedQuery = countQueries.get(methodKey);
        if (storedQuery == null) {

            String query = context.stringValue(Query.class, DataMethod.META_MEMBER_COUNT_QUERY).orElseThrow(() ->
                    new IllegalStateException("No query present in method")
            );
            Class rootEntity = getRequiredRootEntity(context);

            storedQuery = new DefaultStoredQuery<Object, Long>(
                    executableMethod,
                    Long.class,
                    rootEntity,
                    query,
                    DataMethod.META_MEMBER_PARAMETER_BINDING,
                    true
            );
            countQueries.put(methodKey, storedQuery);
        }

        Pageable pageable = storedQuery.hasPageable() ? getPageable(context) : Pageable.UNPAGED;
        //noinspection unchecked
        return new DefaultPreparedQuery(
                context,
                storedQuery,
                storedQuery.getQuery(),
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
        return context.classValue(PREDATOR_ANN_NAME, DataMethod.META_MEMBER_ROOT_ENTITY)
                .orElseThrow(() -> new IllegalStateException("No root entity present in method"));
    }

    /**
     * Retrieve a parameter in the given role for the given type.
     * @param context The context
     * @param role The role
     * @param type The type
     * @param <RT> The generic type
     * @return An optional result
     */
    private <RT> Optional<RT> getParameterInRole(MethodInvocationContext<?, ?> context, @NonNull String role, @NonNull Class<RT> type) {
        return context.stringValue(PREDATOR_ANN_NAME, role).flatMap(name -> {
            RT parameterValue = null;
            Map<String, MutableArgumentValue<?>> params = context.getParameters();
            MutableArgumentValue<?> arg = params.get(name);
            if (arg != null) {
                Object o = arg.getValue();
                if (o != null) {
                    if (type.isInstance(o)) {
                        //noinspection unchecked
                        parameterValue = (RT) o;
                    } else {
                        parameterValue = ConversionService.SHARED
                                .convert(o, type).orElse(null);
                    }
                }
            }
            return Optional.ofNullable(parameterValue);
        });
    }

    /**
     * Resolves the {@link Pageable} for the given context.
     * @param context The pageable
     * @return The pageable or null
     */
    @NonNull
    protected Pageable getPageable(MethodInvocationContext<?, ?> context) {
        Pageable pageable = getParameterInRole(context, TypeRole.PAGEABLE, Pageable.class).orElse(null);
        if (pageable == null) {
            Sort sort = getParameterInRole(context, TypeRole.SORT, Sort.class).orElse(null);
            if (sort != null) {
                int max = context.intValue(PREDATOR_ANN_NAME, META_MEMBER_PAGE_SIZE).orElse(-1);
                int pageIndex = context.intValue(PREDATOR_ANN_NAME, DataMethod.META_MEMBER_PAGE_INDEX).orElse(0);
                if (max > 0) {
                    pageable = Pageable.from(pageIndex, max, sort);
                }
            } else {
                int max = context.intValue(PREDATOR_ANN_NAME, META_MEMBER_PAGE_SIZE).orElse(-1);
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
        String entityParam = context.stringValue(PREDATOR_ANN_NAME, TypeRole.ENTITY)
                .orElseThrow(() -> new IllegalStateException("No entity parameter specified"));

        Object o = context.getParameterValueMap().get(entityParam);
        if (o == null) {
            throw new IllegalArgumentException("Entity argument cannot be null");
        }
        return o;
    }

    private <RT> Map buildParameterValues(MethodInvocationContext<T, R> context, StoredQuery<?, RT> storedQuery) {
        Map<String, Object> parameterValueMap = context.getParameterValueMap();
        Map<?, ?> parameterBinding = storedQuery.getParameterBinding();
        Map parameterValues = new HashMap<>(parameterBinding.size());
        for (Map.Entry entry : parameterBinding.entrySet()) {
            Object name = entry.getKey();
            String argument = (String) entry.getValue();
            storeInParameterValues(context, storedQuery, parameterValueMap, name, argument, parameterValues);

        }
        return parameterValues;
    }

    private <RT> void storeInParameterValues(
            MethodInvocationContext<T, R> context,
            StoredQuery<?, RT> storedQuery,
            Map<String, Object> namedValues,
            Object index,
            String argument,
            Map parameterValues) {
        if (namedValues.containsKey(argument)) {
            parameterValues.put(index, namedValues.get(argument));
        } else {
            String v = storedQuery.getLastUpdatedProperty();
            if (v != null && v.equals(argument)) {

                Class<?> rootEntity = storedQuery.getRootEntity();
                Class<?> lastUpdatedType = getLastUpdatedType(rootEntity, v);
                if (lastUpdatedType == null) {
                    throw new IllegalStateException("Could not establish last updated time for entity: " + rootEntity);
                }
                Object timestamp = ConversionService.SHARED.convert(OffsetDateTime.now(), lastUpdatedType).orElse(null);
                if (timestamp == null) {
                    throw new IllegalStateException("Unsupported date type: " + lastUpdatedType);
                }
                parameterValues.put(index, timestamp);
            } else {
                int i = argument.indexOf('.');
                if (i > -1) {
                    String argumentName = argument.substring(0, i);
                    Object o = namedValues.get(argumentName);
                    if (o != null) {
                        try {
                            BeanWrapper<Object> wrapper = BeanWrapper.getWrapper(o);
                            String prop = argument.substring(i + 1);
                            Object val = wrapper.getRequiredProperty(prop, Object.class);
                            parameterValues.put(index, val);
                        } catch (IntrospectionException e) {
                            throw new DataAccessException("Embedded value [" + o + "] should be annotated with introspected");
                        }
                    }
                } else {
                    Optional<Argument> named = Arrays.stream(context.getArguments())
                            .filter(arg -> {
                                String n = arg.getAnnotationMetadata().stringValue(Parameter.class).orElse(arg.getName());
                                return n.equals(argument);
                            })
                            .findFirst();
                    if (named.isPresent()) {
                        parameterValues.put(index, namedValues.get(named.get().getName()));
                    } else {
                        throw new IllegalArgumentException("Missing query arguments: " + argument);
                    }
                }
            }


        }
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
        PersistentEntity entity = operations.getEntity(rootEntity);
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
        Collection<? extends PersistentProperty> persistentProperties = entity.getPersistentProperties();
        for (PersistentProperty prop : persistentProperties) {
            if (!prop.isReadOnly() && !prop.isGenerated()) {
                String propName = prop.getName();
                if (parameterValues.containsKey(propName)) {

                    Object v = parameterValues.get(propName);
                    if (v == null && !prop.isOptional()) {
                        throw new IllegalArgumentException("Argument [" + propName + "] cannot be null");
                    }
                    wrapper.setProperty(propName, v);
                } else if (!prop.isOptional()) {
                    final Optional<Object> p = wrapper.getProperty(propName, Object.class);
                    if (!p.isPresent()) {
                        throw new IllegalArgumentException("Argument [" + propName + "] cannot be null");
                    }
                }
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
     * Validates null arguments ensuring no argument is null unless declared so.
     * @param context The context
     */
    protected final void validateNullArguments(MethodInvocationContext<T, R> context) {
        Object[] parameterValues = context.getParameterValues();
        for (int i = 0; i < parameterValues.length; i++) {
            Object o = parameterValues[i];
            if (o == null && !context.getArguments()[i].isNullable()) {
                throw new IllegalArgumentException("Argument [" + context.getArguments()[i].getName() + "] value is null and the method parameter is not declared as nullable");
            }
        }
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
        private final @Nullable int[] indexedParameterBinding;
        private final @Nullable String[] parameterPaths;
        private final ExecutableMethod<?, ?> method;
        private final String lastUpdatedProp;
        private final boolean isDto;
        private final boolean isNative;
        private final boolean isNumericPlaceHolder;
        private final boolean hasPageable;
        private final AnnotationMetadata annotationMetadata;
        private final boolean hasIn;
        private final boolean isCount;
        private final DataType[] indexedDataTypes;
        private final String[] parameterNames;
        private final boolean hasResultConsumer;
        private Map<String, Object> queryHints;
        private Set<JoinPath> joinFetchPaths = null;

        /**
         * The default constructor.
         * @param method The target method
         * @param resultType The result type of the query
         * @param rootEntity The root entity of the query
         * @param query The query itself
         * @param isCount Is the query a count query
         */
        DefaultStoredQuery(
                @NonNull ExecutableMethod<?, ?> method,
                @NonNull Class<RT> resultType,
                @NonNull Class<E> rootEntity,
                @NonNull String query,
                @Nullable String parameterBindingMember,
                boolean isCount) {
            this.resultType = ReflectionUtils.getWrapperType(resultType);
            this.rootEntity = rootEntity;
            this.annotationMetadata = method.getAnnotationMetadata();
            this.isNative = method.isTrue(Query.class, "nativeQuery");
            this.hasResultConsumer = method.stringValue(PREDATOR_ANN_NAME, "sqlMappingFunction").isPresent();
            this.isNumericPlaceHolder = method
                    .classValue(RepositoryConfiguration.class, "queryBuilder")
                    .map(c -> c == SqlQueryBuilder.class).orElse(false);
            this.hasIn = isNumericPlaceHolder && query.contains(SqlQueryBuilder.IN_EXPRESSION_START);
            this.hasPageable = method.stringValue(PREDATOR_ANN_NAME, TypeRole.PAGEABLE).isPresent() ||
                                    method.stringValue(PREDATOR_ANN_NAME, TypeRole.SORT).isPresent() ||
                                    method.intValue(PREDATOR_ANN_NAME, META_MEMBER_PAGE_SIZE).orElse(-1) > -1;

            if (isNumericPlaceHolder && method.isTrue(Query.class, DataMethod.META_MEMBER_RAW_QUERY)) {
                Matcher matcher = VARIABLE_PATTERN.matcher(query);
                this.query = matcher.replaceAll("?");
            } else {
                this.query = query;
            }
            this.method = method;
            this.lastUpdatedProp = method.stringValue(PREDATOR_ANN_NAME, TypeRole.LAST_UPDATED_PROPERTY).orElse(null);
            this.isDto = method.isTrue(PREDATOR_ANN_NAME, DataMethod.META_MEMBER_DTO);

            this.isCount = isCount;
            AnnotationValue<DataMethod> annotation = annotationMetadata.getAnnotation(DataMethod.class);
            if (parameterBindingMember != null && annotation != null) {
                this.indexedParameterBinding = annotation.get(
                        parameterBindingMember, int[].class).orElse(EMPTY_INT_ARRAY);
                if (isNumericPlaceHolder) {
                    String[] strArray = annotation.stringValues(parameterBindingMember + "Paths");
                    if (strArray.length == indexedParameterBinding.length) {
                        for (int i = 0; i < strArray.length; i++) {
                            String s = strArray[i];
                            if (StringUtils.isEmpty(s)) {
                                strArray[i] = null;
                            }
                        }
                        this.parameterPaths = strArray;
                    } else {
                        this.parameterPaths = new String[indexedParameterBinding.length];
                    }
                    this.parameterBinding = null;
                    this.parameterNames = null;
                } else {
                    this.parameterBinding = null;
                    this.parameterPaths = annotation.stringValues(parameterBindingMember + "Paths");
                    this.parameterNames = annotation.stringValues(parameterBindingMember + "Names");
                }

            } else {
                this.indexedParameterBinding = EMPTY_INT_ARRAY;
                this.parameterPaths = null;
                this.parameterBinding = null;
                this.parameterNames = null;
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

            if (isNumericPlaceHolder) {
                this.indexedDataTypes = annotationMetadata
                        .getValue(DataMethod.class, DataMethod.META_MEMBER_PARAMETER_TYPE_DEFS, DataType[].class)
                        .orElse(DataType.EMPTY_DATA_TYPE_ARRAY);
            } else {
                this.indexedDataTypes = null;
            }
        }

        @Override
        public String[] getParameterNames() {
            if (parameterNames == null) {
                return StringUtils.EMPTY_STRING_ARRAY;
            }
            return this.parameterNames;
        }

        @Override
        public String[] getIndexedParameterPaths() {
            if (parameterPaths != null) {
                return parameterPaths;
            }
            return StringUtils.EMPTY_STRING_ARRAY;
        }

        @NonNull
        @Override
        public Set<JoinPath> getJoinFetchPaths() {
            if (joinFetchPaths == null) {
                Set<JoinPath> set = method.getAnnotationValuesByType(Join.class).stream().filter(
                        this::isJoinFetch
                ).map(av -> {
                    String path = av.stringValue().orElseThrow(() -> new IllegalStateException("Should not include annotations without a value definition"));
                    String alias = av.stringValue("alias").orElse(null);
                    // only the alias and path is needed, don't materialize the rest
                    return new JoinPath(path, new Association[0], Join.Type.DEFAULT, alias);
                }).collect(Collectors.toSet());
                this.joinFetchPaths = set.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(set);
            }
            return joinFetchPaths;
        }

        @Override
        public boolean isSingleResult() {
            return !isCount() && getJoinFetchPaths().isEmpty();
        }

        @Override
        public boolean hasResultConsumer() {
            return this.hasResultConsumer;
        }

        private boolean isJoinFetch(AnnotationValue<Join> av) {
            if (!av.stringValue().isPresent()) {
                return false;
            }
            Optional<String> type = av.stringValue("type");
            return !type.isPresent() || type.get().contains("FETCH");
        }

        @Override
        public boolean isCount() {
            return isCount;
        }

        @NonNull
        @Override
        public DataType[] getIndexedParameterTypes() {
            if (indexedDataTypes == null) {
                return DataType.EMPTY_DATA_TYPE_ARRAY;
            }
            return this.indexedDataTypes;
        }

        @NonNull
        @Override
        public int[] getIndexedParameterBinding() {
            if (this.indexedParameterBinding != null) {
                return this.indexedParameterBinding;
            }
            return EMPTY_INT_ARRAY;
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
            return annotationMetadata.enumValue(PREDATOR_ANN_NAME, DataMethod.META_MEMBER_RESULT_DATA_TYPE, DataType.class)
                                     .orElse(DataType.OBJECT);
        }

        /**
         * @return The ID type
         */
        @SuppressWarnings("unchecked")
        @Override
        public Optional<Class<?>> getEntityIdentifierType() {
            Optional o = annotationMetadata.classValue(PREDATOR_ANN_NAME, DataMethod.META_MEMBER_ID_TYPE);
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

        @Override
        public boolean hasPageable() {
            return hasPageable;
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

        @Override
        public String getLastUpdatedProperty() {
            return lastUpdatedProp;
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
        private final Pageable pageable;
        private final StoredQuery<E, RT> storedQuery;
        private final String query;
        private final boolean dto;
        private final MethodInvocationContext<T, R> context;

        /**
         * The default constructor.
         *
         * @param context The execution context
         * @param storedQuery The stored query
         * @param finalQuery The final query
         * @param pageable The pageable
         * @param dtoProjection Whether the prepared query is a dto projection
         */
        DefaultPreparedQuery(
                MethodInvocationContext<T, R> context,
                StoredQuery<E, RT> storedQuery,
                String finalQuery,
                @NonNull Pageable pageable,
                boolean dtoProjection) {
            this.context = context;
            this.query = finalQuery;
            this.storedQuery = storedQuery;
            this.pageable = pageable;
            this.dto = dtoProjection;
        }

        @Override
        public String[] getParameterNames() {
            return storedQuery.getParameterNames();
        }

        @Override
        public String[] getIndexedParameterPaths() {
            return storedQuery.getIndexedParameterPaths();
        }

        @Override
        public <RT1> Optional<RT1> getParameterInRole(@NonNull String role, @NonNull Class<RT1> type) {
            return AbstractQueryInterceptor.this.getParameterInRole(context, role, type);
        }

        @Override
        public Class<?> getLastUpdatedType() {
            return AbstractQueryInterceptor.this.getLastUpdatedType(getRootEntity(), getLastUpdatedProperty());
        }

        @Override
        public boolean hasResultConsumer() {
            return storedQuery.hasResultConsumer();
        }

        @NonNull
        @Override
        public int[] getIndexedParameterBinding() {
            return storedQuery.getIndexedParameterBinding();
        }

        @NonNull
        @Override
        public Set<JoinPath> getJoinFetchPaths() {
            return storedQuery.getJoinFetchPaths();
        }

        @Override
        public boolean isSingleResult() {
            return storedQuery.isSingleResult();
        }

        @NonNull
        @Override
        public DataType[] getIndexedParameterTypes() {
            return storedQuery.getIndexedParameterTypes();
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
            return context.getTarget().getClass();
        }

        @NonNull
        @Override
        public Map<String, Object> getParameterValues() {
            return buildParameterValues(context, this);
        }

        @Override
        public Object[] getParameterArray() {
            return context.getParameterValues();
        }

        @Override
        public Argument[] getArguments() {
            return context.getArguments();
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

        @Override
        public boolean hasPageable() {
            return storedQuery.hasPageable();
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
        public String getLastUpdatedProperty() {
            return storedQuery.getLastUpdatedProperty();
        }

        @Nonnull
        @Override
        public String getName() {
            return storedQuery.getName();
        }
    }

}
