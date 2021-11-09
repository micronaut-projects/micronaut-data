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
package io.micronaut.data.runtime.intercept.criteria.async;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.operations.RepositoryOperations;

import java.util.Collections;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Implementation of async unpaged version of {@code findAll(Specification)}.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public class FindAllAsyncSpecificationInterceptor extends AbstractAsyncSpecificationInterceptor<Object, Object> {

    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    protected FindAllAsyncSpecificationInterceptor(RepositoryOperations operations) {
        super(operations);
    }

    @Override
    public Object intercept(RepositoryMethodKey methodKey, MethodInvocationContext<Object, Object> context) {
        PreparedQuery<?, ?> preparedQuery = preparedQueryForCriteria(methodKey, context, Type.FIND_ALL);
        CompletionStage<? extends Iterable<?>>  future = asyncOperations.findAll(preparedQuery);
        return future.thenApply((Function<Iterable<?>, Iterable<Object>>) iterable -> {
            Argument<?> argument = findReturnType(context, LIST_OF_OBJECTS);
            if (argument.getType().isInstance(iterable)) {
                return (Iterable<Object>) iterable;
            }
            Iterable<Object> result = (Iterable<Object>) operations.getConversionService().convert(
                    iterable,
                    argument
            ).orElse(null);
            return result == null ? Collections.emptyList() : result;
        });
    }

}
