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
import io.micronaut.data.intercept.reactive.DeleteReturningOneReactiveInterceptor;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.operations.RepositoryOperations;
import org.jspecify.annotations.NonNull;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Default implementation of {@link DeleteReturningOneReactiveInterceptor}.
 *
 * @author radovanradic
 * @since 5.0.0
 */
@Internal
public final class DefaultDeleteReturningOneReactiveInterceptor extends AbstractPublisherInterceptor implements DeleteReturningOneReactiveInterceptor<Object, Object> {

    public DefaultDeleteReturningOneReactiveInterceptor(@NonNull RepositoryOperations operations) {
        super(operations);
    }

    @Override
    protected Publisher<?> interceptPublisher(RepositoryMethodKey methodKey, MethodInvocationContext<Object, Object> context) {
        PreparedQuery<?, Object> preparedQuery = prepareQuery(methodKey, context);
        Class<?> rootEntityType = preparedQuery.getRootEntity();
        Class<?> resultType = preparedQuery.getResultType();

        if (resultType == rootEntityType) {
            Optional<Object> deleteEntity = findEntityParameter(context, Object.class);
            if (deleteEntity.isPresent()) {
                return reactiveOperations.deleteReturning(getDeleteReturningOperation(context, deleteEntity.get()));
            }
            Optional<Iterable<Object>> deleteEntities = findEntitiesParameter(context, Object.class);
            if (deleteEntities.isPresent()) {
                return Flux.from(reactiveOperations.deleteAllReturning(getDeleteReturningBatchOperation(context, deleteEntities.get()))).singleOrEmpty();
            }
        }
        return Flux.from(reactiveOperations.execute(preparedQuery)).singleOrEmpty();
    }
}

}
