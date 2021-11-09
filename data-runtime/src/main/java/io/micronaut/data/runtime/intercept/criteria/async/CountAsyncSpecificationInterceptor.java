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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.operations.RepositoryOperations;

import java.util.Iterator;
import java.util.concurrent.CompletionStage;

/**
 * Interceptor that supports async count specifications.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public class CountAsyncSpecificationInterceptor extends AbstractAsyncSpecificationInterceptor<Object, CompletionStage<Number>> {

    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    public CountAsyncSpecificationInterceptor(@NonNull RepositoryOperations operations) {
        super(operations);
    }

    @Override
    public CompletionStage<Number> intercept(RepositoryMethodKey methodKey, MethodInvocationContext<Object, CompletionStage<Number>> context) {
        PreparedQuery<?, Long> preparedQuery = preparedQueryForCriteria(methodKey, context, Type.COUNT);
        return asyncOperations.findAll(preparedQuery)
                .thenApply(longs -> {
                    long result = 0L;
                    Iterator<Long> i = longs.iterator();
                    if (i.hasNext()) {
                        result = i.next();
                    }
                    return result;
                });
    }
}
