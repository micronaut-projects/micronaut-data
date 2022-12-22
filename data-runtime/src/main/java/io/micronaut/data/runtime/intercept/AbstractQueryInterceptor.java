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
package io.micronaut.data.runtime.intercept;

import io.micronaut.aop.InvocationContext;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanWrapper;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableArgumentValue;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.exceptions.EmptyResultException;
import io.micronaut.data.intercept.DataInterceptor;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.runtime.AbstractPreparedDataOperation;
import io.micronaut.data.model.runtime.BatchOperation;
import io.micronaut.data.model.runtime.DefaultStoredDataOperation;
import io.micronaut.data.model.runtime.DeleteBatchOperation;
import io.micronaut.data.model.runtime.DeleteOperation;
import io.micronaut.data.model.runtime.EntityInstanceOperation;
import io.micronaut.data.model.runtime.EntityOperation;
import io.micronaut.data.model.runtime.InsertBatchOperation;
import io.micronaut.data.model.runtime.InsertOperation;
import io.micronaut.data.model.runtime.PagedQuery;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.model.runtime.UpdateBatchOperation;
import io.micronaut.data.model.runtime.UpdateOperation;
import io.micronaut.data.operations.HintsCapableRepository;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.runtime.query.DefaultPagedQueryResolver;
import io.micronaut.data.runtime.query.DefaultPreparedQueryResolver;
import io.micronaut.data.runtime.query.DefaultStoredQueryResolver;
import io.micronaut.data.runtime.query.MethodContextAwareStoredQueryDecorator;
import io.micronaut.data.runtime.query.PagedQueryResolver;
import io.micronaut.data.runtime.query.PreparedQueryDecorator;
import io.micronaut.data.runtime.query.PreparedQueryResolver;
import io.micronaut.data.runtime.query.StoredQueryDecorator;
import io.micronaut.data.runtime.query.StoredQueryResolver;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static io.micronaut.data.intercept.annotation.DataMethod.META_MEMBER_PAGE_SIZE;

/**
 * Abstract interceptor that executes a {@link Query}.
 *
 * @param <T> The declaring type
 * @param <R> The return type
 * @author graemerocher
 * @since 1.0
 */
public abstract class AbstractQueryInterceptor<T, R> implements DataInterceptor<T, R> {
    protected final ConversionService conversionService;
    protected final RepositoryOperations operations;
    protected final PreparedQueryResolver preparedQueryResolver;
    private final ConcurrentMap<RepositoryMethodKey, StoredQuery> countQueries = new ConcurrentHashMap<>(50);
    private final ConcurrentMap<RepositoryMethodKey, StoredQuery> queries = new ConcurrentHashMap<>(50);
    private final StoredQueryResolver storedQueryResolver;
    private final MethodContextAwareStoredQueryDecorator storedQueryDecorator;
    private final PagedQueryResolver pagedQueryResolver;
    private final PreparedQueryDecorator preparedQueryDecorator;

    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    protected AbstractQueryInterceptor(@NonNull RepositoryOperations operations) {
        ArgumentUtils.requireNonNull("operations", operations);
        this.conversionService = operations.getConversionService();
        this.operations = operations;
        this.storedQueryResolver = operations instanceof StoredQueryResolver ? (StoredQueryResolver) operations : new DefaultStoredQueryResolver() {
            @Override
            protected HintsCapableRepository getHintsCapableRepository() {
                return operations;
            }
        };
        if (operations instanceof MethodContextAwareStoredQueryDecorator) {
            storedQueryDecorator = (MethodContextAwareStoredQueryDecorator) operations;
        } else if (operations instanceof StoredQueryDecorator) {
            StoredQueryDecorator decorator = (StoredQueryDecorator) operations;
            storedQueryDecorator = new MethodContextAwareStoredQueryDecorator() {
                @Override
                public <E, K> StoredQuery<E, K> decorate(MethodInvocationContext<?, ?> context, StoredQuery<E, K> storedQuery) {
                    return decorator.decorate(storedQuery);
                }
            };
        } else {
            storedQueryDecorator = new MethodContextAwareStoredQueryDecorator() {
                @Override
                public <E, K> StoredQuery<E, K> decorate(MethodInvocationContext<?, ?> context, StoredQuery<E, K> storedQuery) {
                    return storedQuery;
                }
            };
        }
        this.preparedQueryResolver = operations instanceof PreparedQueryResolver ? (PreparedQueryResolver) operations : new DefaultPreparedQueryResolver() {
            @Override
            protected ConversionService getConversionService() {
                return operations.getConversionService();
            }
        };
        this.preparedQueryDecorator = operations instanceof PreparedQueryDecorator ? (PreparedQueryDecorator) operations : new PreparedQueryDecorator() {
            @Override
            public <E, K> PreparedQuery<E, K> decorate(PreparedQuery<E, K> preparedQuery) {
                return preparedQuery;
            }
        };
        this.pagedQueryResolver = operations instanceof PagedQueryResolver ? (PagedQueryResolver) operations : new DefaultPagedQueryResolver();
    }

    /**
     * Returns parameter values with respect of {@link Parameter} annotation.
     *
     * @param context The method invocation context
     * @return The parameters value map
     */
    @NonNull
    protected Map<String, Object> getParameterValueMap(MethodInvocationContext<?, ?> context) {
        Argument<?>[] arguments = context.getArguments();
        Object[] parameterValues = context.getParameterValues();
        Map<String, Object> valueMap = new LinkedHashMap<>(arguments.length);
        for (int i = 0; i < parameterValues.length; i++) {
            Object parameterValue = parameterValues[i];
            Argument arg = arguments[i];
            valueMap.put(arg.getAnnotationMetadata().stringValue(Parameter.class).orElseGet(arg::getName), parameterValue);
        }
        return valueMap;
    }

    /**
     * Returns the return type.
     *
     * @param context The context
     * @return the return type
     */
    protected Argument<?> getReturnType(MethodInvocationContext<?, ?> context) {
        return context.getReturnType().asArgument();
    }

    @Nullable
    protected final Object convertOne(MethodInvocationContext<?, ?> context, @Nullable Object o) {
        Argument<?> argumentType = getReturnType(context);
        Class<?> type = argumentType.getType();
        if (o == null) {
            if (type == Optional.class) {
                return Optional.empty();
            }
            if (argumentType.isDeclaredNonNull() || !argumentType.isNullable()
                    && !context.getReturnType().asArgument().isNullable()) {
                throw new EmptyResultException();
            }
            return null;
        }
        boolean isOptional = false;
        if (type == Optional.class) {
            argumentType = argumentType.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
            isOptional = true;
        }
        o = convertOne(o, argumentType);
        if (isOptional) {
            return Optional.of(o);
        }
        return o;
    }

    protected final Object convertOne(Object o, Argument<?> argumentType) {
        if (argumentType.isInstance(o)) {
            return o;
        }
        return operations.getConversionService().convertRequired(o, argumentType);
    }

    /**
     * Prepares a query for the given context.
     *
     * @param key     The method key
     * @param context The context
     * @return The query
     */
    @NonNull
    protected final PreparedQuery<?, ?> prepareQuery(RepositoryMethodKey key, MethodInvocationContext<T, R> context) {
        return prepareQuery(key, context, null);
    }

    /**
     * Prepares a query for the given context.
     *
     * @param <RT>       The result generic type
     * @param methodKey  The method key
     * @param context    The context
     * @param resultType The result type
     * @return The query
     */
    @NonNull
    protected final <RT> PreparedQuery<?, RT> prepareQuery(RepositoryMethodKey methodKey,
                                                           MethodInvocationContext<T, R>
                                                                   context, Class<RT> resultType) {
        return prepareQuery(methodKey, context, resultType, false);
    }

    /**
     * Prepares a query for the given context.
     *
     * @param <RT>       The result generic type
     * @param methodKey  The method key
     * @param context    The context
     * @param resultType The result type
     * @param isCount    Is count query
     * @return The query
     */
    @NonNull
    protected final <RT> PreparedQuery<?, RT> prepareQuery(RepositoryMethodKey methodKey,
                                                           MethodInvocationContext<T, R> context,
                                                           Class<RT> resultType,
                                                           boolean isCount) {
        validateNullArguments(context);
        StoredQuery<?, RT> storedQuery = findStoreQuery(methodKey, context, resultType, isCount);
        Pageable pageable = storedQuery.hasPageable() ? getPageable(context) : Pageable.UNPAGED;
        PreparedQuery<?, RT> preparedQuery = preparedQueryResolver.resolveQuery(context, storedQuery, pageable);
        return preparedQueryDecorator.decorate(preparedQuery);
    }

    private <E, RT> StoredQuery<E, RT> findStoreQuery(MethodInvocationContext<?, ?> context, boolean isCount) {
        RepositoryMethodKey key = new RepositoryMethodKey(context.getTarget(), context.getExecutableMethod());
        return findStoreQuery(key, context, null, isCount);
    }

    private <E, RT> StoredQuery<E, RT> findStoreQuery(RepositoryMethodKey methodKey, MethodInvocationContext<?, ?> context, Class<RT> resultType, boolean isCount) {
        StoredQuery<E, RT> storedQuery = queries.get(methodKey);
        if (storedQuery == null) {
            Class<E> rootEntity = context.classValue(DataMethod.NAME, DataMethod.META_MEMBER_ROOT_ENTITY)
                    .orElseThrow(() -> new IllegalStateException("No root entity present in method"));
            if (resultType == null) {
                //noinspection unchecked
                resultType = (Class<RT>) context.classValue(DataMethod.NAME, DataMethod.META_MEMBER_RESULT_TYPE)
                        .orElse(rootEntity);
            }
            storedQuery = storedQueryResolver.resolveQuery(context, rootEntity, resultType);
            storedQuery = storedQueryDecorator.decorate(context, storedQuery);
            queries.put(methodKey, storedQuery);
        }
        return storedQuery;
    }

    /**
     * Prepares a query for the given context.
     *
     * @param methodKey The method key
     * @param context   The context
     * @return The query
     */
    @NonNull
    protected final PreparedQuery<?, Number> prepareCountQuery(RepositoryMethodKey methodKey, @NonNull MethodInvocationContext<T, R> context) {
        StoredQuery storedQuery = countQueries.get(methodKey);
        if (storedQuery == null) {
            Class rootEntity = getRequiredRootEntity(context);
            storedQuery = storedQueryResolver.resolveCountQuery(context, rootEntity, Long.class);
            storedQuery = storedQueryDecorator.decorate(context, storedQuery);
            countQueries.put(methodKey, storedQuery);
        }

        Pageable pageable = storedQuery.hasPageable() ? getPageable(context) : Pageable.UNPAGED;
        //noinspection unchecked
        PreparedQuery preparedQuery = preparedQueryResolver.resolveCountQuery(context, storedQuery, pageable);
        return preparedQueryDecorator.decorate(preparedQuery);
    }

    /**
     * Obtains the root entity or throws an exception if it not available.
     *
     * @param context The context
     * @param <E>     The entity type
     * @return The root entity type
     * @throws IllegalStateException If the root entity is unavailable
     */
    @NonNull
    protected <E> Class<E> getRequiredRootEntity(MethodInvocationContext context) {
        Class aClass = context.classValue(DataMethod.NAME, DataMethod.META_MEMBER_ROOT_ENTITY).orElse(null);
        if (aClass != null) {
            return aClass;
        } else {
            final AnnotationValue<Annotation> ann = context.getDeclaredAnnotation(DataMethod.NAME);
            if (ann != null) {
                aClass = ann.classValue(DataMethod.META_MEMBER_ROOT_ENTITY).orElse(null);
                if (aClass != null) {
                    return aClass;
                }
            }

            throw new IllegalStateException("No root entity present in method");
        }
    }

    /**
     * Retrieve an entity parameter value in role.
     *
     * @param context The context
     * @param type    The type
     * @param <RT>    The generic type
     * @return An result
     */
    protected <RT> RT getEntityParameter(MethodInvocationContext<?, ?> context, @NonNull Class<RT> type) {
        return getRequiredParameterInRole(context, TypeRole.ENTITY, type);
    }

    /**
     * Retrieve an entities parameter value in role.
     *
     * @param context The context
     * @param type    The type
     * @param <RT>    The generic type
     * @return An result
     */
    protected <RT> Iterable<RT> getEntitiesParameter(MethodInvocationContext<?, ?> context, @NonNull Class<RT> type) {
        return getRequiredParameterInRole(context, TypeRole.ENTITIES, Iterable.class);
    }

    /**
     * Find an entity parameter value in role.
     *
     * @param context The context
     * @param type    The type
     * @param <RT>    The generic type
     * @return An result
     */
    protected <RT> Optional<RT> findEntityParameter(MethodInvocationContext<?, ?> context, @NonNull Class<RT> type) {
        return getParameterInRole(context, TypeRole.ENTITY, type);
    }

    /**
     * Fid an entities parameter value in role.
     *
     * @param context The context
     * @param type    The type
     * @param <RT>    The generic type
     * @return An result
     */
    protected <RT> Optional<Iterable<RT>> findEntitiesParameter(MethodInvocationContext<?, ?> context, @NonNull Class<RT> type) {
        Optional parameterInRole = getParameterInRole(context, TypeRole.ENTITIES, Iterable.class);
        return (Optional<Iterable<RT>>) parameterInRole;
    }

    /**
     * Retrieve a parameter in the given role for the given type.
     *
     * @param context The context
     * @param role    The role
     * @param type    The type
     * @param <RT>    The generic type
     * @return An result
     */
    private <RT> RT getRequiredParameterInRole(MethodInvocationContext<?, ?> context, @NonNull String role, @NonNull Class<RT> type) {
        return getParameterInRole(context, role, type).orElseThrow(() -> new IllegalStateException("Cannot find parameter with role: " + role));
    }

    /**
     * Retrieve a parameter in the given role for the given type.
     *
     * @param context The context
     * @param role    The role
     * @param type    The type
     * @param <RT>    The generic type
     * @return An optional result
     */
    private <RT> Optional<RT> getParameterInRole(MethodInvocationContext<?, ?> context, @NonNull String role, @NonNull Class<RT> type) {
        return context.stringValue(DataMethod.NAME, role).flatMap(name -> {
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
                        parameterValue = operations.getConversionService()
                                .convert(o, type).orElse(null);
                    }
                }
            }
            return Optional.ofNullable(parameterValue);
        });
    }

    /**
     * Resolves the {@link Pageable} for the given context.
     *
     * @param context The pageable
     * @return The pageable or null
     */
    @NonNull
    protected Pageable getPageable(MethodInvocationContext<?, ?> context) {
        Pageable pageable = getParameterInRole(context, TypeRole.PAGEABLE, Pageable.class).orElse(null);
        if (pageable == null) {
            Sort sort = getParameterInRole(context, TypeRole.SORT, Sort.class).orElse(null);
            int max = context.intValue(DataMethod.NAME, META_MEMBER_PAGE_SIZE).orElse(-1);
            if (sort != null) {
                int pageIndex = context.intValue(DataMethod.NAME, DataMethod.META_MEMBER_PAGE_INDEX).orElse(0);
                if (max > 0) {
                    pageable = Pageable.from(pageIndex, max, sort);
                } else {
                    pageable = Pageable.from(sort);
                }
            } else if (max > -1) {
                return Pageable.from(0, max);
            }
        }
        return pageable != null ? pageable : Pageable.UNPAGED;
    }

    /**
     * Return whether the metadata indicates the instance is nullable.
     *
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
     *
     * @param context The context
     * @return The entity
     */
    @NonNull
    protected Object getRequiredEntity(MethodInvocationContext<T, ?> context) {
        String entityParam = context.stringValue(DataMethod.NAME, TypeRole.ENTITY)
                .orElseThrow(() -> new IllegalStateException("No entity parameter specified"));

        Object o = context.getParameterValueMap().get(entityParam);
        if (o == null) {
            throw new IllegalArgumentException("Entity argument cannot be null");
        }
        return o;
    }

    /**
     * Instantiate the given entity for the given parameter values.
     *
     * @param rootEntity      The entity
     * @param parameterValues The parameter values
     * @return The entity
     * @throws IllegalArgumentException if the entity cannot be instantiated due to an illegal argument
     */
    @NonNull
    protected Object instantiateEntity(@NonNull Class<?> rootEntity, @NonNull Map<String, Object> parameterValues) {
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
                } else if (prop.isRequired()) {
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
     * Get the paged query for the given context.
     *
     * @param context The context
     * @param <E>     The entity type
     * @return The paged query
     */
    @NonNull
    protected <E> PagedQuery<E> getPagedQuery(@NonNull MethodInvocationContext context) {
        return pagedQueryResolver.resolveQuery(context, getRequiredRootEntity(context), getPageable(context));
    }

    /**
     * Get the insert batch operation for the given context.
     *
     * @param context  The context
     * @param iterable The iterable
     * @param <E>      The entity type
     * @return The paged query
     */
    @NonNull
    protected <E> InsertBatchOperation<E> getInsertBatchOperation(@NonNull MethodInvocationContext context, @NonNull Iterable<E> iterable) {
        @SuppressWarnings("unchecked") Class<E> rootEntity = getRequiredRootEntity(context);
        return getInsertBatchOperation(context, rootEntity, iterable);
    }

    /**
     * Get the insert batch operation for the given context.
     *
     * @param <E>        The entity type
     * @param context    The context
     * @param rootEntity The root entity
     * @param iterable   The iterable
     * @return The paged query
     */
    @NonNull
    protected <E> InsertBatchOperation<E> getInsertBatchOperation(@NonNull MethodInvocationContext context, Class<E> rootEntity, @NonNull Iterable<E> iterable) {
        return new DefaultInsertBatchOperation<>(context, rootEntity, iterable);
    }

    /**
     * Get the batch operation for the given context.
     *
     * @param context The context
     * @param <E>     The entity type
     * @return The paged query
     */
    @SuppressWarnings("unchecked")
    @NonNull
    protected <E> InsertOperation<E> getInsertOperation(@NonNull MethodInvocationContext context) {
        E o = (E) getRequiredEntity(context);
        return new DefaultInsertOperation<>(context, o);
    }

    /**
     * Get the batch operation for the given context.
     *
     * @param context The context
     * @param <E>     The entity type
     * @return The paged query
     */
    @SuppressWarnings("unchecked")
    @NonNull
    protected <E> UpdateOperation<E> getUpdateOperation(@NonNull MethodInvocationContext<T, ?> context) {
        return getUpdateOperation(context, (E) getRequiredEntity(context));
    }

    /**
     * Get the batch operation for the given context.
     *
     * @param context The context
     * @param entity  The entity instance
     * @param <E>     The entity type
     * @return The paged query
     */
    @SuppressWarnings("unchecked")
    @NonNull
    protected <E> UpdateOperation<E> getUpdateOperation(@NonNull MethodInvocationContext<T, ?> context, E entity) {
        return new DefaultUpdateOperation<>(context, entity);
    }

    /**
     * Get the update all batch operation for the given context.
     *
     * @param <E>        The entity type
     * @param rootEntity The root entitry
     * @param context    The context
     * @param iterable   The iterable
     * @return The paged query
     */
    @NonNull
    protected <E> UpdateBatchOperation<E> getUpdateAllBatchOperation(@NonNull MethodInvocationContext<T, ?> context, Class<E> rootEntity, @NonNull Iterable<E> iterable) {
        return new DefaultUpdateBatchOperation<>(context, rootEntity, iterable);
    }

    /**
     * Get the delete operation for the given context.
     *
     * @param context The context
     * @param entity  The entity
     * @param <E>     The entity type
     * @return The paged query
     */
    @NonNull
    protected <E> DeleteOperation<E> getDeleteOperation(@NonNull MethodInvocationContext<T, ?> context, @NonNull E entity) {
        return new DefaultDeleteOperation<>(context, entity);
    }

    /**
     * Get the delete all batch operation for the given context.
     *
     * @param context The context
     * @param <E>     The entity type
     * @return The paged query
     */
    @NonNull
    protected <E> DeleteBatchOperation<E> getDeleteAllBatchOperation(@NonNull MethodInvocationContext<T, ?> context) {
        @SuppressWarnings("unchecked") Class<E> rootEntity = getRequiredRootEntity(context);
        return new DefaultDeleteAllBatchOperation<>(context, rootEntity);
    }

    /**
     * Get the delete batch operation for the given context.
     *
     * @param context  The context
     * @param iterable The iterable
     * @param <E>      The entity type
     * @return The paged query
     */
    @NonNull
    protected <E> DeleteBatchOperation<E> getDeleteBatchOperation(@NonNull MethodInvocationContext<T, ?> context, @NonNull Iterable<E> iterable) {
        @SuppressWarnings("unchecked") Class<E> rootEntity = (Class<E>) getRequiredRootEntity(context);
        return getDeleteBatchOperation(context, rootEntity, iterable);
    }

    /**
     * Get the delete batch operation for the given context.
     *
     * @param <E>        The entity type
     * @param context    The context
     * @param rootEntity The root entity
     * @param iterable   The iterable
     * @return The paged query
     */
    @NonNull
    protected <E> DeleteBatchOperation<E> getDeleteBatchOperation(@NonNull MethodInvocationContext<T, ?> context, Class<E> rootEntity, @NonNull Iterable<E> iterable) {
        return new DefaultDeleteBatchOperation<>(context, rootEntity, iterable);
    }

    /**
     * Get the batch operation for the given context.
     *
     * @param context The context
     * @param entity  The entity
     * @param <E>     The entity type
     * @return The paged query
     */
    @NonNull
    protected <E> InsertOperation<E> getInsertOperation(@NonNull MethodInvocationContext<T, ?> context, E entity) {
        return new DefaultInsertOperation<>(context, entity);
    }

    /**
     * Validates null arguments ensuring no argument is null unless declared so.
     *
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
     * Count the items.
     *
     * @param iterable the iterable
     * @return the size
     */
    protected int count(Iterable<?> iterable) {
        if (iterable instanceof Collection) {
            return ((Collection<?>) iterable).size();
        }
        Iterator<?> iterator = iterable.iterator();
        int i = 0;
        while (iterator.hasNext()) {
            i++;
            iterator.next();
        }
        return i;
    }

    /**
     * Is the type a number.
     *
     * @param type The type
     * @return True if is a number
     */
    protected boolean isNumber(@Nullable Class<?> type) {
        if (type == null) {
            return false;
        }
        if (type.isPrimitive()) {
            return ClassUtils.getPrimitiveType(type.getName()).map(aClass ->
                    Number.class.isAssignableFrom(ReflectionUtils.getWrapperType(aClass))
            ).orElse(false);
        }
        return Number.class.isAssignableFrom(type);
    }

    /**
     * Default implementation of {@link InsertOperation}.
     *
     * @param <E> The entity type
     */
    private final class DefaultInsertOperation<E> extends AbstractEntityOperation<E> implements InsertOperation<E> {
        private final E entity;

        DefaultInsertOperation(MethodInvocationContext<?, ?> method, E entity) {
            super(method, (Class<E>) entity.getClass());
            this.entity = entity;
        }

        @Override
        public E getEntity() {
            return entity;
        }

    }

    /**
     * Default implementation of {@link DeleteOperation}.
     *
     * @param <E> The entity type
     */
    private class DefaultDeleteOperation<E> extends AbstractEntityInstanceOperation<E> implements DeleteOperation<E> {
        DefaultDeleteOperation(MethodInvocationContext<?, ?> method, E entity) {
            super(method, entity);
        }
    }

    /**
     * Default implementation of {@link UpdateOperation}.
     *
     * @param <E> The entity type
     */
    private final class DefaultUpdateOperation<E> extends AbstractEntityOperation<E> implements UpdateOperation<E> {
        private final E entity;

        DefaultUpdateOperation(MethodInvocationContext<?, ?> method, E entity) {
            super(method, (Class<E>) entity.getClass());
            this.entity = entity;
        }

        @Override
        public E getEntity() {
            return entity;
        }

    }

    private abstract class AbstractEntityInstanceOperation<E> extends AbstractEntityOperation<E> implements EntityInstanceOperation<E> {
        private final E entity;

        AbstractEntityInstanceOperation(MethodInvocationContext<?, ?> method, E entity) {
            super(method, (Class<E>) entity.getClass());
            this.entity = entity;
        }

        @NonNull
        @Override
        public E getEntity() {
            return entity;
        }

    }

    private abstract class AbstractEntityOperation<E> extends AbstractPreparedDataOperation<E> implements EntityOperation<E> {
        protected final MethodInvocationContext<?, ?> method;
        protected final Class<E> rootEntity;
        protected StoredQuery<E, ?> storedQuery;

        AbstractEntityOperation(MethodInvocationContext<?, ?> method, Class<E> rootEntity) {
            super((MethodInvocationContext<?, E>) method, new DefaultStoredDataOperation<>(method.getExecutableMethod()));
            this.method = method;
            this.rootEntity = rootEntity;
        }

        @Override
        public StoredQuery<E, ?> getStoredQuery() {
            if (storedQuery == null) {
                String queryString = method.stringValue(Query.class).orElse(null);
                if (queryString == null) {
                    return null;
                }
                storedQuery = findStoreQuery(method, false);
            }
            return storedQuery;
        }

        @Override
        public <RT1> Optional<RT1> getParameterInRole(@NonNull String role, @NonNull Class<RT1> type) {
            return AbstractQueryInterceptor.this.getParameterInRole(method, role, type);
        }

        @NonNull
        @Override
        public Class<E> getRootEntity() {
            return rootEntity;
        }

        @NonNull
        @Override
        public Class<?> getRepositoryType() {
            return method.getTarget().getClass();
        }

        @NonNull
        @Override
        public String getName() {
            return method.getDeclaringType().getSimpleName() + "." + method.getMethodName();
        }

        @Override
        public InvocationContext<?, ?> getInvocationContext() {
            return method;
        }
    }

    /**
     * Default implementation of {@link InsertBatchOperation}.
     *
     * @param <E> The entity type
     */
    private class DefaultInsertBatchOperation<E> extends DefaultBatchOperation<E> implements InsertBatchOperation<E> {
        DefaultInsertBatchOperation(MethodInvocationContext<?, ?> method, @NonNull Class<E> rootEntity, Iterable<E> iterable) {
            super(method, rootEntity, iterable);
        }

        @Override
        public List<InsertOperation<E>> split() {
            List<InsertOperation<E>> inserts = new ArrayList<>(10);
            for (E e : iterable) {
                inserts.add(new DefaultInsertOperation<>(method, e));
            }
            return inserts;
        }
    }

    /**
     * Default implementation of {@link DeleteBatchOperation}.
     *
     * @param <E> The entity type
     */
    private class DefaultDeleteAllBatchOperation<E> extends DefaultBatchOperation<E> implements DeleteBatchOperation<E> {

        DefaultDeleteAllBatchOperation(MethodInvocationContext<?, ?> method, @NonNull Class<E> rootEntity) {
            super(method, rootEntity, Collections.emptyList());
        }

        @Override
        public boolean all() {
            return true;
        }

        @Override
        public List<DeleteOperation<E>> split() {
            throw new IllegalStateException("Split is not supported for delete all operation!");
        }
    }

    /**
     * Default implementation of {@link DeleteBatchOperation}.
     *
     * @param <E> The entity type
     */
    private class DefaultDeleteBatchOperation<E> extends DefaultBatchOperation<E> implements DeleteBatchOperation<E> {

        DefaultDeleteBatchOperation(MethodInvocationContext<?, ?> method, @NonNull Class<E> rootEntity, Iterable<E> iterable) {
            super(method, rootEntity, iterable);
        }

        public List<DeleteOperation<E>> split() {
            List<DeleteOperation<E>> deletes = new ArrayList<>(10);
            for (E e : iterable) {
                deletes.add(new DefaultDeleteOperation<>(method, e));
            }
            return deletes;
        }

    }

    /**
     * Default implementation of {@link UpdateBatchOperation}.
     *
     * @param <E> The entity type
     */
    private class DefaultUpdateBatchOperation<E> extends DefaultBatchOperation<E> implements UpdateBatchOperation<E> {

        DefaultUpdateBatchOperation(MethodInvocationContext<?, ?> method, @NonNull Class<E> rootEntity, Iterable<E> iterable) {
            super(method, rootEntity, iterable);
        }

        public List<UpdateOperation<E>> split() {
            List<UpdateOperation<E>> updates = new ArrayList<>(10);
            for (E e : iterable) {
                updates.add(new DefaultUpdateOperation<>(method, e));
            }
            return updates;
        }

    }

    /**
     * Default implementation of {@link BatchOperation}.
     *
     * @param <E> The entity type
     */
    private class DefaultBatchOperation<E> extends AbstractEntityOperation<E> implements BatchOperation<E> {
        protected final Iterable<E> iterable;

        public DefaultBatchOperation(MethodInvocationContext<?, ?> method, @NonNull Class<E> rootEntity, Iterable<E> iterable) {
            super(method, rootEntity);
            this.iterable = iterable;
        }

        @Override
        public Iterator<E> iterator() {
            return iterable.iterator();
        }

    }

}
