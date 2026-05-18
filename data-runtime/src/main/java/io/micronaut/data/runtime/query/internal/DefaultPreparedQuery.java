/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.runtime.query.internal;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableArgumentValue;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.intercept.annotation.DataMethodQuery;
import io.micronaut.data.model.CursoredPage;
import io.micronaut.data.model.CursoredPageable;
import io.micronaut.data.model.Limit;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.runtime.DefaultStoredDataOperation;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.StoredQuery;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Represents a prepared query.
 *
 * @param <E>  The entity type
 * @param <RT> The result type
 */
@Internal
public final class DefaultPreparedQuery<E, RT> extends DefaultStoredDataOperation<RT> implements DelegateStoredQuery<E, RT>, PreparedQuery<E, RT> {
    private final Pageable pageable;
    private final StoredQuery<E, RT> storedQuery;
    private final String query;
    private final boolean dto;
    private final MethodInvocationContext<?, ?> context;
    private final ConversionService conversionService;
    @Nullable
    private final Limit limit;

    /**
     * The default constructor.
     *
     * @param context           The execution context
     * @param storedQuery       The stored query
     * @param finalQuery        The final query
     * @param pageable          The pageable
     * @param limit             The limit
     * @param dtoProjection     Whether the prepared query is a dto projection
     * @param conversionService The conversion service
     */
    public DefaultPreparedQuery(
            MethodInvocationContext<?, ?> context,
            StoredQuery<E, RT> storedQuery,
            String finalQuery,
            @NonNull Pageable pageable,
            @NonNull Limit limit,
            boolean dtoProjection,
            ConversionService conversionService) {
        super(context);
        this.context = context;
        this.query = finalQuery;
        this.storedQuery = storedQuery;
        this.dto = dtoProjection;
        this.conversionService = conversionService;
        if (pageable.getMode() == Pageable.Mode.OFFSET && hasReturnTypeInRole(TypeRole.CURSORED_PAGE, CursoredPage.class, context, conversionService)) {
            if (pageable.getNumber() == 0) {
                pageable = CursoredPageable.from(pageable.getSize(), pageable.getSort());
            } else {
                throw new IllegalArgumentException("Cursored page with the offset mode page request needs to start from the first page number: 0, but was: " + pageable.getNumber());
            }
        }
        this.pageable = pageable.withSort(storedQuery.getSort().orders(pageable.getOrderBy()));
        this.limit = Limit.UNLIMITED.equals(limit) && !storedQuery.isCount() ? Limit.of(pageable.getSize(), pageable.getOffset()) : limit;
    }

    /**
     * Check the return role from the method context.
     *
     * @param role              The role
     * @param type              The type
     * @param methodContext     The method context
     * @param conversionService The conversion service
     * @return The optional parameter
     */
    public static boolean hasReturnTypeInRole(@NonNull String role,
                                              @NonNull Class<?> type,
                                              @NonNull MethodInvocationContext<?, ?> methodContext,
                                              @NonNull ConversionService conversionService) {
        return methodContext.stringValue(DataMethod.NAME, DataMethodQuery.META_MEMBER_RETURN_TYPE_ROLE)
            .filter(typeRole -> typeRole.equals(role))
            .map(ignore -> conversionService.canConvert(methodContext.getReturnType().getType(), type))
            .orElse(false);
    }

    /**
     * Find a parameter in role from the method context.
     *
     * @param role              The role
     * @param type              The type of the parameter in role
     * @param methodContext     The method context
     * @param conversionService The conversion service
     * @param <RT1>             The type
     * @return The optional parameter
     */
    @NonNull
    public static <RT1> Optional<RT1> getParameterInRole(@NonNull String role,
                                                         @NonNull Class<RT1> type,
                                                         @NonNull MethodInvocationContext<?, ?> methodContext,
                                                         @NonNull ConversionService conversionService) {
        return getParameterInRole(role, Argument.of(type), methodContext, conversionService);
    }

    /**
     * Find a parameter in role from the method context.
     *
     * @param role              The role
     * @param type              The type of the parameter in role
     * @param methodContext     The method context
     * @param conversionService The conversion service
     * @param <RT1>             The type
     * @return The optional parameter
     */
    @NonNull
    public static <RT1> Optional<RT1> getParameterInRole(@NonNull String role,
                                                         @NonNull Argument<RT1> type,
                                                         @NonNull MethodInvocationContext<?, ?> methodContext,
                                                         @NonNull ConversionService conversionService) {
        return methodContext.stringValue(DataMethod.NAME, role).flatMap(name -> {
            MutableArgumentValue<?> arg = methodContext.getParameters().get(name);
            if (arg == null) {
                return Optional.empty();
            }
            Object o = arg.getValue();
            if (o == null) {
                return Optional.empty();
            }
            if (type.isInstance(o)) {
                //noinspection unchecked
                return Optional.of((RT1) o);
            }
            return conversionService.convert(o, ConversionContext.of(type));
        });
    }

    /**
     * Find the parameters in role from the method context.
     *
     * @param role              The role
     * @param type              The type of the parameter in role
     * @param methodContext     The method context
     * @param conversionService The conversion service
     * @param <RT1>             The type
     * @return The list of types
     */
    @NonNull
    public static <RT1> List<RT1> getParametersInRole(@NonNull String role,
                                                      @NonNull Class<RT1> type,
                                                      @NonNull MethodInvocationContext<?, ?> methodContext,
                                                      @NonNull ConversionService conversionService) {
        return  getParametersInRole(role, Argument.of(type), methodContext, conversionService);
    }

    /**
     * Find the parameters in role from the method context.
     *
     * @param role              The role
     * @param type              The type of the parameter in role
     * @param methodContext     The method context
     * @param conversionService The conversion service
     * @param <RT1>             The type
     * @return The list of types
     */
    @NonNull
    public static <RT1> List<RT1> getParametersInRole(@NonNull String role,
                                                       @NonNull Argument<RT1> type,
                                                       @NonNull MethodInvocationContext<?, ?> methodContext,
                                                       @NonNull ConversionService conversionService) {
        AnnotationValue<Annotation> annotation = methodContext.getAnnotation(DataMethod.NAME);
        if (annotation == null) {
            return List.of();
        }
        List<AnnotationValue<Annotation>> roles = annotation.getAnnotations(DataMethodQuery.META_MEMBER_PARAMETERS_TYPE_ROLES);
        return roles.stream()
            .filter(a -> a.stringValue().orElseThrow().equals(role))
            .flatMap(a -> {
                Object value = methodContext.getParameterValues()[a.intValue("parameterIndex").orElseThrow()];
                if (value == null) {
                    return Stream.empty();
                }
                if (type.isInstance(value)) {
                    //noinspection unchecked
                    return Stream.of((RT1) value);
                }
                return conversionService.convert(value, type).stream();
            }).toList();
    }

    @NonNull
    public static <T> List<T> getParametersOfType(@NonNull Argument<T> type,
                                                  @NonNull MethodInvocationContext<?, ?> methodContext,
                                                  @NonNull ConversionService conversionService) {
        Argument<?>[] arguments = methodContext.getArguments();
        Object[] values = methodContext.getParameterValues();
        if (arguments.length == 0 || values.length == 0) {
            return List.of();
        }
        return java.util.stream.IntStream.range(0, Math.min(arguments.length, values.length))
            .mapToObj(i -> {
                Object value = values[i];
                if (value == null) {
                    return Stream.<T>empty();
                }
                if (type.isInstance(value)) {
                    //noinspection unchecked
                    return Stream.of((T) value);
                }
                if (type.getType().isAssignableFrom(arguments[i].getType())) {
                    return conversionService.convert(value, type).stream();
                }
                return Stream.<T>empty();
            })
            .flatMap(s -> s)
            .toList();
    }

    @Override
    public ConversionService getConversionService() {
        return conversionService;
    }

    /**
     * @return The context
     */
    public MethodInvocationContext<?, ?> getContext() {
        return context;
    }

    @Override
    public Class<E> getRootEntity() {
        return storedQuery.getRootEntity();
    }

    @Override
    public Map<String, Object> getQueryHints() {
        return storedQuery.getQueryHints();
    }

    @Override
    public boolean isRawQuery() {
        return storedQuery.isRawQuery();
    }

    @Override
    public StoredQuery<E, RT> getStoredQueryDelegate() {
        return storedQuery;
    }

    @Override
    public <RT1> Optional<RT1> getParameterInRole(@NonNull String role, @NonNull Class<RT1> type) {
        return getParameterInRole(role, type, context, conversionService);
    }

    @Override
    public <RT1> List<RT1> getParametersInRole(String role, Class<RT1> type) {
        return getParametersInRole(role, type, context, conversionService);
    }

    @Override
    public Class<?> getRepositoryType() {
        return context.getTarget().getClass();
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
    public boolean isDtoProjection() {
        return dto;
    }

    @NonNull
    @Override
    public String getQuery() {
        return query;
    }

    @NonNull
    @Override
    public ConvertibleValues<Object> getAttributes() {
        return context.getAttributes();
    }

    @NonNull
    @Override
    public Optional<Object> getAttribute(CharSequence name) {
        return context.getAttribute(name);
    }

    @NonNull
    @Override
    public <T> Optional<T> getAttribute(CharSequence name, Class<T> type) {
        return context.getAttribute(name, type);
    }

    @Override
    public Sort getSort() {
        return pageable.getSort();
    }

    @Override
    public Limit getQueryLimit() {
        if (limit != null) {
            return limit;
        }
        return pageable.getLimit();
    }
}
