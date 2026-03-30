/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.data.runtime.operations.internal.query;

import io.micronaut.aop.InvocationContext;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.beans.BeanWrapper;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.JsonDataType;
import io.micronaut.data.model.Limit;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.vector.search.Score;
import io.micronaut.data.model.vector.search.ScoringFunction;
import io.micronaut.data.model.vector.search.Similarity;
import io.micronaut.data.model.vector.search.SimilarityNormalizer;
import io.micronaut.data.model.runtime.DelegatingQueryParameterBinding;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.runtime.operations.internal.sql.SqlStoredQuery;
import io.micronaut.data.runtime.query.internal.DelegateStoredQuery;
import io.micronaut.inject.annotation.EvaluatedAnnotationValue;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;

import static io.micronaut.data.runtime.query.internal.DefaultPreparedQuery.getParametersOfType;

/**
 * Implementation of {@link BindableParametersStoredQuery}.
 *
 * @param <E> The entity type
 * @param <R> The result type
 * @author Denis Stepanov
 * @since 3.8.0
 */
@Internal
public class DefaultBindableParametersStoredQuery<E, R> implements BindableParametersStoredQuery<E, R>, DelegateStoredQuery<E, R> {

    private final StoredQuery<E, R> storedQuery;
    private final RuntimePersistentEntity<E> runtimePersistentEntity;
    private final ConversionService conversionService;

    /**
     * @param storedQuery             The stored query
     * @param runtimePersistentEntity The persistent entity
     * @param conversionService       The conversion service
     */
    public DefaultBindableParametersStoredQuery(StoredQuery<E, R> storedQuery,
                                                RuntimePersistentEntity<E> runtimePersistentEntity,
                                                ConversionService conversionService) {
        this.storedQuery = storedQuery;
        this.runtimePersistentEntity = runtimePersistentEntity;
        this.conversionService = conversionService;
        Objects.requireNonNull(storedQuery, "Query cannot be null");
    }

    @Override
    public RuntimePersistentEntity<E> getPersistentEntity() {
        return runtimePersistentEntity;
    }

    @Override
    public StoredQuery<E, R> getStoredQueryDelegate() {
        return storedQuery;
    }

    @Override
    public void bindParameters(Binder binder,
                               @Nullable
                               InvocationContext<?, ?> invocationContext,
                               @Nullable
                               E entity,
                               @Nullable
                               Map<QueryParameterBinding, Object> previousValues) {
        for (QueryParameterBinding queryParameterBinding : storedQuery.getQueryBindings()) {
            bindParameter(binder, invocationContext, entity, previousValues, queryParameterBinding);
        }
    }

    protected final void bindParameter(Binder binder,
                                       @Nullable InvocationContext<?, ?> invocationContext,
                                       @Nullable E entity,
                                       @Nullable Map<QueryParameterBinding, Object> previousValues,
                                       QueryParameterBinding binding) {
        RuntimePersistentEntity<E> persistentEntity = getPersistentEntity();
        Class<?> parameterConverter = binding.getParameterConverterClass();
        Object value = binding.getValue();
        RuntimePersistentProperty<Object> persistentProperty = null;
        Argument<?> argument = null;
        boolean skipExpansion = false;
        if (value == null) {
            if (binding.isExpression()) {
                Objects.requireNonNull(invocationContext, invocationIsRequiredMessage());
                AnnotationValue<?> annotationValue = storedQuery.getParameterExpressions().get(binding.getName());
                if (annotationValue == null) {
                    throw new IllegalStateException("Required annotation value for parameter expression: " + binding.getName());
                }
                if (annotationValue instanceof EvaluatedAnnotationValue<?> evaluatedAnnotationValue) {
                    evaluatedAnnotationValue = evaluatedAnnotationValue.withArguments(
                        Objects.requireNonNull(invocationContext.getTarget()),
                        invocationContext.getParameterValues()
                    );
                    value = evaluatedAnnotationValue.get("expression", Argument.OBJECT_ARGUMENT).orElseThrow();
                } else {
                    throw new IllegalStateException("Required evaluated annotation value for parameter expression: " + binding.getName());
                }
            } else if (binding.getParameterIndex() != -1) {
                Objects.requireNonNull(invocationContext, invocationIsRequiredMessage());
                value = resolveParameterValue(binding, invocationContext.getParameterValues());
                argument = invocationContext.getArguments()[binding.getParameterIndex()];
            } else if (binding.isAutoPopulated()) {
                PersistentPropertyPath pp = getRequiredPropertyPath(binding, persistentEntity);
                persistentProperty = (RuntimePersistentProperty<Object>) pp.getProperty();
                if (binding.isRequiresPreviousPopulatedValue()) {
                    if (previousValues != null) {
                        value = previousValues.get(binding);
                    }
                } else {
                    if (entity == null) {
                        Object previousValue = null;
                        QueryParameterBinding previousPopulatedValueParameter = binding.getPreviousPopulatedValueParameter();
                        if (previousPopulatedValueParameter != null) {
                            if (previousPopulatedValueParameter.getParameterIndex() == -1) {
                                throw new IllegalStateException("Previous value parameter cannot be bind!");
                            }
                            Objects.requireNonNull(invocationContext, invocationIsRequiredMessage());
                            previousValue = resolveParameterValue(previousPopulatedValueParameter, invocationContext.getParameterValues());
                        }
                        value = binder.autoPopulateRuntimeProperty(persistentProperty, previousValue);
                    } else {
                        value = pp.getPropertyValue(entity);
                    }
                }
                value = binder.convert(value, persistentProperty);
                parameterConverter = null;
            } else if (entity != null) {
                if (isJsonEntity() && binding.getDataType() == DataType.JSON) {
                    value = entity;
                } else {
                    PersistentPropertyPath pp = getRequiredPropertyPath(binding, persistentEntity);
                    value = pp.getPropertyValue(entity);
                    persistentProperty = (RuntimePersistentProperty<Object>) pp.getProperty();
                }
            } else {
                // If this expression below is false that means value was set/provided in binding object, so we
                // shouldn't throw an error, otherwise we throw an error as we couldn't resolve the value.
                // This is the case with runtime criteria
                if (binding.getParameterIndex() != -1 || binding.isAutoPopulated()) {
                    int currentIndex = binder.currentIndex();
                    if (currentIndex != -1) {
                        throw new IllegalStateException("Invalid query [" + getQuery() + "]. Unable to establish parameter value for parameter at position: " + currentIndex);
                    } else {
                        throw new IllegalStateException("Invalid query [" + getQuery() + "]. Unable to establish parameter value for parameter: " + binding.getName());
                    }
                } else {
                    // Otherwise, value got from binding object meaning it was set to null, so we can at least check
                    // since value is null whether the property is nullable
                    String[] propertyPath = binding.getPropertyPath();
                    if (propertyPath != null) {
                        PersistentPropertyPath pp = persistentEntity.getPropertyPath(propertyPath);
                        if (pp != null && pp.getProperty().isRequired()) {
                            throw new IllegalStateException("Field [" + pp.getProperty().getName() + "] does not allow null value.");
                        }
                    }
                }
            }
        } else if (value instanceof EvaluatedAnnotationValue<?> evaluatedAnnotationValue) {
            Objects.requireNonNull(invocationContext, invocationIsRequiredMessage());
            value = evaluatedAnnotationValue.withArguments(
                invocationContext.getTarget(),
                invocationContext.getParameterValues()
            ).get(AnnotationMetadata.VALUE_MEMBER, Object.class).orElse(null);
        }

        if (persistentProperty != null) {
            argument = persistentProperty.getArgument();
            if (binding.getDataType() != persistentProperty.getDataType()) {
                RuntimePersistentProperty<?> finalPersistentProperty = persistentProperty;
                binding = new DelegatingQueryParameterBinding(binding) {

                    @Override
                    public DataType getDataType() {
                        return finalPersistentProperty.getDataType();
                    }

                    @Override
                    public JsonDataType getJsonDataType() {
                        return finalPersistentProperty.getJsonDataType();
                    }
                };
            }
        }
        if (binding.getRole() != null) {
            value = switch (binding.getRole()) {
                case TypeRole.PAGEABLE, TypeRole.PAGEABLE_REQUIRED -> conversionService.convertRequired(value, Pageable.class);
                case TypeRole.LIMIT -> conversionService.convertRequired(value, Limit.class);
                case TypeRole.SORT -> conversionService.convertRequired(value, Sort.class);
                default ->
                    throw new IllegalArgumentException("Unsupported role " + binding.getRole());
            };
            skipExpansion = true;
        }
        value = unwrapVectorSearchValue(value, invocationContext);
        List<Object> values;
        if (binding.isExpandable()) {
            if (skipExpansion) {
                return;
            }
            values = expandValue(value, binding.getDataType());
        } else {
            values = null;
        }
        if (values != null && values.isEmpty()) {
            // Empty collections / array should always set at least one value
            value = null;
            values = null;
        }
        if (values == null) {
            if (parameterConverter != null) {
                value = binder.convert(parameterConverter, value, argument);
            } else if (persistentProperty != null && !binding.isAutoPopulated()) {
                value = binder.convert(value, persistentProperty);
            }
            binder.bindOne(binding, value);
        } else {
            values = new ArrayList<>(values);
            for (ListIterator<Object> iterator = values.listIterator(); iterator.hasNext(); ) {
                Object v = iterator.next();
                if (parameterConverter != null) {
                    v = binder.convert(parameterConverter, v, argument);
                } else if (persistentProperty != null && !binding.isAutoPopulated()) {
                    v = binder.convert(v, persistentProperty);
                }
                iterator.set(v);
            }
            binder.bindMany(binding, values);
        }
    }

    @Nullable
    private Object unwrapVectorSearchValue(@Nullable Object value,
                                           @Nullable InvocationContext<?, ?> invocationContext) {
        if (value instanceof Score(double score)) {
            return score;
        }
        if (value instanceof Similarity(double similarity)) {
            ScoringFunction scoringFunction = resolveScoringFunction(invocationContext);
            if (scoringFunction != null) {
                return SimilarityNormalizer.forScoringFunction(scoringFunction).getScore(similarity);
            }
            return similarity;
        }
        return value;
    }

    @Nullable
    private ScoringFunction resolveScoringFunction(@Nullable InvocationContext<?, ?> invocationContext) {
        if (!(invocationContext instanceof MethodInvocationContext<?, ?> methodInvocationContext)) {
            return defaultScoringFunction();
        }
        List<ScoringFunction> scoringFunctions = getParametersOfType(
            Argument.of(ScoringFunction.class),
            methodInvocationContext,
            conversionService
        );
        if (scoringFunctions.isEmpty()) {
            return defaultScoringFunction();
        }
        if (scoringFunctions.size() > 1) {
            throw new IllegalArgumentException("Only one ScoringFunction parameter is allowed for vector derived search queries");
        }
        return scoringFunctions.getFirst();
    }

    @Nullable
    private ScoringFunction defaultScoringFunction() {
        if (!(storedQuery instanceof SqlStoredQuery<?, ?> sqlStoredQuery)) {
            return null;
        }
        return switch (sqlStoredQuery.getDialect()) {
            case POSTGRES, ORACLE -> ScoringFunction.COSINE;
            case MYSQL -> ScoringFunction.L2_EUCLIDEAN;
            default -> null;
        };
    }

    private Object resolveParameterValue(QueryParameterBinding queryParameterBinding, Object[] parameterArray) {
        Object value = parameterArray[queryParameterBinding.getParameterIndex()];
        String[] parameterBindingPath = queryParameterBinding.getParameterBindingPath();
        if (parameterBindingPath != null) {
            for (String prop : parameterBindingPath) {
                if (value == null) {
                    break;
                }
                value = BeanWrapper.getWrapper(value).getRequiredProperty(prop, Argument.OBJECT_ARGUMENT);
            }
        }
        return value;
    }

    @Nullable
    private List<Object> expandValue(@Nullable Object value, @Nullable DataType dataType) {
        // Special case for byte array, we want to support a list of byte[] convertible values
        if (value == null || dataType != null && dataType.isArray() && dataType != DataType.BYTE_ARRAY || value instanceof byte[]) {
            // not expanded
            return null;
        } else if (value instanceof Iterable<?> iterable) {
            return (List<Object>) CollectionUtils.iterableToList(iterable);
        } else if (value.getClass().isArray()) {
            int len = Array.getLength(value);
            if (len == 0) {
                return Collections.emptyList();
            } else {
                var list = new ArrayList<>(len);
                for (int j = 0; j < len; j++) {
                    Object o = Array.get(value, j);
                    list.add(o);
                }
                return list;
            }
        } else {
            // not expanded
            return null;
        }
    }

    protected final <T> PersistentPropertyPath getRequiredPropertyPath(QueryParameterBinding queryParameterBinding, RuntimePersistentEntity<T> persistentEntity) {
        String[] propertyPath = queryParameterBinding.getRequiredPropertyPath();
        PersistentPropertyPath pp = persistentEntity.getPropertyPath(propertyPath);
        if (pp == null) {
            throw new IllegalStateException("Cannot find property: " + String.join(".", propertyPath));
        }
        return pp;
    }

    private String invocationIsRequiredMessage() {
        return "Invocation context is required!";
    }

}
