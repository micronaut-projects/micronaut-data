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
import io.micronaut.core.type.MutableArgumentValue;
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
import io.micronaut.inject.ExecutableMethod;

import javax.annotation.Nonnull;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Abstract interceptor that executes a {@link io.micronaut.data.annotation.Query}.
 * @param <T> The declaring type
 * @param <R> The return type
 * @since 1.0
 * @author graemerocher
 */
public abstract class AbstractQueryInterceptor<T, R> implements PredatorInterceptor<T, R> {
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("(:[a-zA-Z0-9]+)");
    private static final String PREDATOR_ANN_NAME = PredatorMethod.class.getName();
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
        validateNullArguments(context);
        ExecutableMethod<T, R> executableMethod = context.getExecutableMethod();
        Class<?> repositoryType = context.getTarget().getClass();
        MethodKey key = newMethodKey(repositoryType, executableMethod);
        StoredQuery<?, RT> storedQuery = findQueries.get(key);
        if (storedQuery == null) {
            Class<?> rootEntity = context.classValue(PREDATOR_ANN_NAME, PredatorMethod.META_MEMBER_ROOT_ENTITY)
                    .orElseThrow(() -> new IllegalStateException("No root entity present in method"));
            if (resultType == null) {
                //noinspection unchecked
                resultType = (Class<RT>) context.classValue(PREDATOR_ANN_NAME, PredatorMethod.META_MEMBER_RESULT_TYPE).orElse(rootEntity);
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
                context,
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
                    context.isPresent(PREDATOR_ANN_NAME, PredatorMethod.META_MEMBER_COUNT_PARAMETERS) ? PredatorMethod.META_MEMBER_COUNT_PARAMETERS : null
            );
            countQueries.put(key, storedQuery);
        }

        Pageable pageable = getPageable(context);
        Map<String, Object> parameterValues = buildParameterValues(context, storedQuery, storedQuery.getRootEntity());
        //noinspection unchecked
        return new DefaultPreparedQuery(
                context,
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
        return context.classValue(PREDATOR_ANN_NAME, PredatorMethod.META_MEMBER_ROOT_ENTITY)
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
                int max = context.intValue(PREDATOR_ANN_NAME, PredatorMethod.META_MEMBER_PAGE_SIZE).orElse(-1);
                int pageIndex = context.intValue(PREDATOR_ANN_NAME, PredatorMethod.META_MEMBER_PAGE_INDEX).orElse(0);
                if (max > 0) {
                    pageable = Pageable.from(pageIndex, max, sort);
                }
            } else {
                int max = context.intValue(PREDATOR_ANN_NAME, PredatorMethod.META_MEMBER_PAGE_SIZE).orElse(-1);
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

    private <RT> Map buildParameterValues(MethodInvocationContext<T, R> context, StoredQuery<?, RT> storedQuery, Class<?> rootEntity) {
        Map<String, Object> parameterValueMap = context.getParameterValueMap();
        if (storedQuery.useNumericPlaceholders()) {
            String[] indexedParameterBinding = storedQuery.getIndexedParameterBinding();
            Map parameterValues = new HashMap<>(indexedParameterBinding.length);
            for (int index = 0; index < indexedParameterBinding.length; index++) {
                String argument = indexedParameterBinding[index];
                storeInParameterValues(context, storedQuery, rootEntity, parameterValueMap, index + 1, argument, parameterValues);
            }
            return parameterValues;
        } else {
            Map<?, ?> parameterBinding = storedQuery.getParameterBinding();
            Map parameterValues = new HashMap<>(parameterBinding.size());
            for (Map.Entry entry : parameterBinding.entrySet()) {
                Object name = entry.getKey();
                String argument = (String) entry.getValue();
                storeInParameterValues(context, storedQuery, rootEntity, parameterValueMap, name, argument, parameterValues);

            }
            return parameterValues;
        }
    }

    private <RT> void storeInParameterValues(MethodInvocationContext<T, R> context, StoredQuery<?, RT> storedQuery, Class<?> rootEntity, Map<String, Object> namedValues, Object index, String argument, Map parameterValues) {
        String v = storedQuery.getLastUpdatedProperty().orElse(null);
        if (namedValues.containsKey(argument)) {
            parameterValues.put(index, namedValues.get(argument));
        } else if (v != null && v.equals(argument)) {
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
        List<? extends PersistentProperty> persistentProperties = entity.getPersistentProperties();
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
     * Validates null arguments ensuring no argument is null unless declared so.
     * @param context The context
     */
    protected void validateNullArguments(MethodInvocationContext<T, R> context) {
        Collection<MutableArgumentValue<?>> values =
                context.getParameters().values();

        for (MutableArgumentValue<?> value : values) {
            Object o = value.getValue();
            if (o == null && !value.getAnnotationMetadata().hasAnnotation("javax.annotation.Nullable")) {
                throw new IllegalArgumentException("Argument [" + value.getName() + "] value is null and the method parameter is not declared as nullable");
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
        private final @Nullable String[] indexedParameterBinding;
        private final ExecutableMethod<?, ?> method;
        private final String lastUpdatedProp;
        private final boolean isDto;
        private final boolean isNative;
        private final boolean isNumericPlaceHolder;
        private final AnnotationMetadata annotationMetadata;
        private final boolean hasIn;
        private final boolean isCount;
        private final Map<String, DataType> dataTypes;
        private final DataType[] indexedDataTypes;
        private Map<String, Object> queryHints;
        private Set<String> joinFetchPaths = null;

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
            this.isNumericPlaceHolder = method
                    .classValue(RepositoryConfiguration.class, "queryBuilder")
                    .map(c -> c == SqlQueryBuilder.class).orElse(false);
            this.hasIn = isNumericPlaceHolder && query.contains(SqlQueryBuilder.IN_EXPRESSION_START);

            if (isNumericPlaceHolder && method.isTrue(Query.class, PredatorMethod.META_MEMBER_RAW_QUERY)) {
                Matcher matcher = VARIABLE_PATTERN.matcher(query);
                this.query = matcher.replaceAll("?");
            } else {
                this.query = query;
            }
            this.method = method;
            this.lastUpdatedProp = method.stringValue(PREDATOR_ANN_NAME, TypeRole.LAST_UPDATED_PROPERTY).orElse(null);
            this.isDto = method.isTrue(PREDATOR_ANN_NAME, PredatorMethod.META_MEMBER_DTO);

            this.isCount = parameterBindingMember != null && parameterBindingMember.startsWith("count");
            AnnotationValue<PredatorMethod> annotation = annotationMetadata.getAnnotation(PredatorMethod.class);
            if (parameterBindingMember != null && annotation != null) {

                    List<AnnotationValue<Property>> parameterData = annotation.getAnnotations(parameterBindingMember,
                            Property.class);
                    if (CollectionUtils.isNotEmpty(parameterData)) {

                        if (isNumericPlaceHolder) {
                            this.indexedParameterBinding = new String[parameterData.size()];
                            this.parameterBinding = null;
                            for (AnnotationValue<Property> annotationValue : parameterData) {
                                String argument = annotationValue.stringValue("value").orElse(null);
                                int i = annotationValue.intValue("name").orElse(1);
                                indexedParameterBinding[i - 1] = argument;
                            }
                        } else {
                            Map parameterValues = new HashMap(parameterData.size());
                            for (AnnotationValue<Property> annotationValue : parameterData) {
                                Object placeHolderName;
                                placeHolderName = annotationValue.stringValue("name").orElse(null);
                                String argument = annotationValue.stringValue("value").orElse(null);
                                if (placeHolderName != null && argument != null) {
                                    parameterValues.put(placeHolderName, argument);
                                }
                            }
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
                if (isNumericPlaceHolder) {
                    this.dataTypes = null;
                    this.indexedDataTypes = new DataType[typeDefs.size()];
                    for (AnnotationValue<TypeDef> typeDef : typeDefs) {
                        typeDef.enumValue("type", DataType.class).ifPresent(dataType -> {
                            String[] values = typeDef.stringValues("names");
                            for (String value : values) {
                                indexedDataTypes[Integer.valueOf(value) - 1] = dataType;
                            }
                        });
                    }
                } else {
                    this.dataTypes = new HashMap<>(typeDefs.size());
                    this.indexedDataTypes = null;
                    for (AnnotationValue<TypeDef> typeDef : typeDefs) {
                        typeDef.enumValue("type", DataType.class).ifPresent(dataType -> {
                            String[] values = typeDef.stringValues("names");
                            for (String value : values) {
                                dataTypes.put(value, dataType);
                            }
                        });
                    }
                }


            } else {
                this.indexedDataTypes = null;
                this.dataTypes = null;
            }
        }

        @NonNull
        @Override
        public Set<String> getJoinFetchPaths() {
            if (joinFetchPaths == null) {
                Set<String> set = method.getAnnotationValuesByType(Join.class).stream().filter(
                        this::isJoinFetch
                ).map(av -> av.stringValue().orElseThrow(() -> new IllegalStateException("Should not include annotations without a value definition")))
                        .collect(Collectors.toSet());
                joinFetchPaths = set.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(set);
            }
            return joinFetchPaths;
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
        public Map<String, DataType> getParameterTypes() {
            if (dataTypes == null) {
                return Collections.emptyMap();
            }
            return this.dataTypes;
        }

        @NonNull
        @Override
        public DataType[] getIndexedParameterTypes() {
            if (indexedDataTypes == null) {
                return new DataType[0];
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
            return annotationMetadata.findAnnotation(PREDATOR_ANN_NAME)
                                     .flatMap(av -> av.enumValue(PredatorMethod.META_MEMBER_RESULT_DATA_TYPE, DataType.class))
                                     .orElse(DataType.OBJECT);
        }

        /**
         * @return The ID type
         */
        @SuppressWarnings("unchecked")
        @Override
        public Optional<Class<?>> getEntityIdentifierType() {
            Optional o = annotationMetadata.classValue(PREDATOR_ANN_NAME, PredatorMethod.META_MEMBER_ID_TYPE);
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
        public String[] getIndexedParameterBinding() {
            if (indexedParameterBinding == null) {
                return StringUtils.EMPTY_STRING_ARRAY;
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
        private final MethodInvocationContext<T, R> context;

        /**
         * The default constructor.
         * @param context The execution context
         * @param repositoryType The repository type
         * @param storedQuery The stored query
         * @param finalQuery The final query
         * @param parameterValues The parameter values
         * @param pageable The pageable
         */
        DefaultPreparedQuery(
                MethodInvocationContext<T, R> context,
                Class<?> repositoryType,
                StoredQuery<E, RT> storedQuery,
                String finalQuery,
                @Nullable Map<String, Object> parameterValues,
                @Nullable Pageable pageable) {
            this(context, repositoryType, storedQuery, finalQuery, parameterValues, pageable, storedQuery.isDtoProjection());
        }

        /**
         * The default constructor.
         *
         * @param context The execution context
         * @param repositoryType The repository type
         * @param storedQuery The stored query
         * @param finalQuery The final query
         * @param parameterValues The parameter values
         * @param pageable The pageable
         * @param dtoProjection Whether the prepared query is a dto projection
         */
        DefaultPreparedQuery(
                MethodInvocationContext<T, R> context,
                Class<?> repositoryType,
                StoredQuery<E, RT> storedQuery,
                String finalQuery,
                @Nullable Map parameterValues,
                @Nullable Pageable pageable,
                boolean dtoProjection) {
            this.context = context;
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

        @Override
        public <RT1> Optional<RT1> getParameterInRole(@NonNull String role, @NonNull Class<RT1> type) {
            return AbstractQueryInterceptor.this.getParameterInRole(context, role, type);
        }

        @NonNull
        @Override
        public Set<String> getJoinFetchPaths() {
            return storedQuery.getJoinFetchPaths();
        }

        @NonNull
        @Override
        public DataType[] getIndexedParameterTypes() {
            return storedQuery.getIndexedParameterTypes();
        }

        @NonNull
        @Override
        public String[] getIndexedParameterBinding() {
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
