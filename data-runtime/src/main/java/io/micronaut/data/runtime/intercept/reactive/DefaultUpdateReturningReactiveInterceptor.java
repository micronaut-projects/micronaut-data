/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.runtime.intercept.reactive;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.intercept.reactive.UpdateReturningReactiveInterceptor;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.operations.RepositoryOperations;
import org.reactivestreams.Publisher;

/**
 * Default reactive interceptor for update-returning repository methods.
 * <p>
 * This interceptor executes a prepared update-returning query via
 * {@link io.micronaut.data.operations.reactive.ReactiveRepositoryOperations#execute(io.micronaut.data.model.runtime.PreparedQuery)}.
 * <p>
 * This interceptor is backend-agnostic; it relies on the concrete {@code ReactiveRepositoryOperations}
 * implementation to provide the actual semantics.
 *
 * @author radovanradic
 * @since 5.0.0
 */
@Internal
public final class DefaultUpdateReturningReactiveInterceptor extends AbstractPublisherInterceptor
    implements UpdateReturningReactiveInterceptor<Object, Object> {

    /**
     * @param operations The repository operations
     */
    public DefaultUpdateReturningReactiveInterceptor(RepositoryOperations operations) {
        super(operations);
    }

    @Override
    protected Publisher<?> interceptPublisher(RepositoryMethodKey methodKey, MethodInvocationContext<Object, Object> context) {
        PreparedQuery<?, Object> preparedQuery = prepareQuery(methodKey, context);
        return reactiveOperations.execute(preparedQuery);
    }
}
