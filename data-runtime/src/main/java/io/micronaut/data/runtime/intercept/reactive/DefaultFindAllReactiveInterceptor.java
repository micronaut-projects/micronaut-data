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
package io.micronaut.data.runtime.intercept.reactive;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.intercept.reactive.FindAllReactiveInterceptor;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.operations.RepositoryOperations;
import org.reactivestreams.Publisher;

/**
 * Default implementation of {@link FindAllReactiveInterceptor}.
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultFindAllReactiveInterceptor extends AbstractReactiveInterceptor<Object, Object>
        implements FindAllReactiveInterceptor<Object, Object> {
    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    protected DefaultFindAllReactiveInterceptor(@NonNull RepositoryOperations operations) {
        super(operations);
    }

    @Override
    public Object intercept(RepositoryMethodKey methodKey, MethodInvocationContext<Object, Object> context) {
        Publisher<?> publisher;
        if (context.hasAnnotation(Query.class)) {
            PreparedQuery<?, ?> preparedQuery = prepareQuery(methodKey, context);
            publisher = reactiveOperations.findAll(preparedQuery);

        } else {
            publisher = reactiveOperations.findAll(getPagedQuery(context));
        }
        return Publishers.convertPublisher(publisher, context.getReturnType().getType());
    }
}
