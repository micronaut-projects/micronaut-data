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
package io.micronaut.data.runtime.intercept.criteria.reactive;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.operations.RepositoryOperations;
import org.reactivestreams.Publisher;

/**
 * Interceptor that supports reactive exists specifications.
 *
 * @author Nick Hensel
 * @since 3.8
 */
@Internal
public class ExistsReactiveSpecificationInterceptor extends AbstractReactiveSpecificationInterceptor<Object, Publisher<Boolean>> {
    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    protected ExistsReactiveSpecificationInterceptor(RepositoryOperations operations) {
        super(operations);
    }

    @Override
    public Publisher<Boolean> intercept(RepositoryMethodKey methodKey, MethodInvocationContext<Object, Publisher<Boolean>> context) {
        PreparedQuery<?, Boolean> preparedQuery = preparedQueryForCriteria(methodKey, context, Type.EXISTS);
        return Publishers.convertPublisher(
            conversionService,
            reactiveOperations.exists(preparedQuery),
            context.getReturnType().getType()
        );
    }
}
