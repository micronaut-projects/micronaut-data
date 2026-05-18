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
package io.micronaut.data.runtime.intercept.criteria.async;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.ArgumentUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.operations.async.AsyncCapableRepository;
import io.micronaut.data.operations.async.AsyncCriteriaRepositoryOperations;
import io.micronaut.data.operations.async.AsyncRepositoryOperations;
import io.micronaut.data.operations.reactive.ReactiveCriteriaRepositoryOperations;
import io.micronaut.data.runtime.intercept.criteria.AbstractSpecificationInterceptor;
import jakarta.persistence.criteria.CriteriaQuery;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Abstract async specification interceptor.
 *
 * @param <T> The declaring type
 * @param <R> The return type
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public abstract class AbstractAsyncSpecificationInterceptor<T, R> extends AbstractSpecificationInterceptor<T, R> {
    protected static final Argument<List<Object>> LIST_OF_OBJECTS = Argument.listOf(Object.class);

    protected final AsyncRepositoryOperations asyncOperations;
    @Nullable
    protected final AsyncCriteriaRepositoryOperations asyncCriteriaOperations;

    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    protected AbstractAsyncSpecificationInterceptor(RepositoryOperations operations) {
        super(validateAsyncRepositoryOperations(operations));
        this.asyncOperations = ((AsyncCapableRepository) operations).async();

        AsyncCriteriaRepositoryOperations criteriaOperations = findRepositoryOperations(AsyncCriteriaRepositoryOperations.class);
        if (criteriaOperations == null) {
            ReactiveCriteriaRepositoryOperations reactiveCriteriaOperations = findRepositoryOperations(ReactiveCriteriaRepositoryOperations.class);
            if (reactiveCriteriaOperations != null) {
                criteriaOperations = new ReactiveAsyncCriteriaRepositoryOperations(reactiveCriteriaOperations);
            }
        }
        asyncCriteriaOperations = criteriaOperations;
        if (asyncCriteriaOperations != null) {
            criteriaBuilder = asyncCriteriaOperations.getCriteriaBuilder();
        }
    }

    private static RepositoryOperations validateAsyncRepositoryOperations(RepositoryOperations operations) {
        ArgumentUtils.requireNonNull("operations", operations);
        if (operations instanceof AsyncCapableRepository) {
            return operations;
        }
        throw new DataAccessException("Datastore of type [" + operations.getClass() + "] does not support asynchronous operations");
    }

    final AsyncCriteriaRepositoryOperations getAsyncCriteriaRepositoryOperations(RepositoryMethodKey methodKey,
                                                                                 MethodInvocationContext<?, ?> context,
                                                                                 @Nullable
                                                                                 Pageable pageable) {
        if (asyncCriteriaOperations != null) {
            return asyncCriteriaOperations;
        }
        QueryBuilder sqlQueryBuilder = getQueryBuilder(methodKey, context);
        return new PreparedQueryAsyncCriteriaRepositoryOperations(
            criteriaBuilder,
            asyncOperations,
            operations,
            context,
            sqlQueryBuilder,
            getRequiredRootEntity(context),
            pageable
        );
    }

    @NonNull
    protected final CompletionStage<Iterable<Object>> findAllAsync(RepositoryMethodKey methodKey, MethodInvocationContext<T, R> context) {
        CriteriaQuery<Object> criteriaQuery = buildQuery(methodKey, context);
        Pageable pageable = applyPaginationAndSort(getPageable(context), criteriaQuery, true);
        if (asyncCriteriaOperations != null) {
            if (pageable != null && !pageable.isUnpaged()) {
                return asyncCriteriaOperations.findAll(criteriaQuery, (int) pageable.getOffset(), pageable.getSize()).thenApply(m -> m);
            }
            return asyncCriteriaOperations.findAll(criteriaQuery).thenApply(m -> m);
        }
        return getAsyncCriteriaRepositoryOperations(methodKey, context, pageable).findAll(criteriaQuery).thenApply(m -> m);
    }

    @Override
    protected final Argument<?> getReturnType(MethodInvocationContext<?, ?> context) {
        return findReturnType(context, Argument.OBJECT_ARGUMENT);
    }

    protected final Argument<?> findReturnType(MethodInvocationContext<?, ?> context, Argument<?> defaultArg) {
        if (context.isSuspend()) {
            return context.getReturnType().asArgument();
        }
        return context.getReturnType().asArgument().getFirstTypeVariable().orElse(defaultArg);
    }

    /**
     * Convert a number argument if necessary.
     *
     * @param context The method context
     * @param number  The number
     * @return The result
     */
    @Nullable
    protected Number convertNumberToReturnType(MethodInvocationContext<?, ?> context, Number number) {
        Argument<?> firstTypeVar = findReturnType(context, Argument.LONG);
        Class<?> type = firstTypeVar.getType();
        if (type == Object.class || type == Void.class) {
            return null;
        }
        if (number == null) {
            number = 0;
        }
        if (!type.isInstance(number)) {
            return (Number) operations.getConversionService().convert(number, firstTypeVar)
                .orElseThrow(() -> new IllegalStateException("Unsupported number type for return type: " + firstTypeVar));
        } else {
            return number;
        }
    }
}
