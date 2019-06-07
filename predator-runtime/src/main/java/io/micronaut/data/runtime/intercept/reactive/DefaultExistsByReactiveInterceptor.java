/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import io.micronaut.data.intercept.annotation.PredatorMethod;
import io.micronaut.data.intercept.reactive.ExistsByReactiveInterceptor;
import io.micronaut.data.model.PreparedQuery;
import io.micronaut.data.operations.RepositoryOperations;
import io.reactivex.Flowable;

/**
 * Default implementation of {@link ExistsByReactiveInterceptor}.
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultExistsByReactiveInterceptor extends AbstractReactiveInterceptor<Object, Object>
        implements ExistsByReactiveInterceptor<Object, Object> {
    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    protected DefaultExistsByReactiveInterceptor(@NonNull RepositoryOperations operations) {
        super(operations);
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Class idType = context.classValue(PredatorMethod.class, PredatorMethod.META_MEMBER_ID_TYPE)
                .orElseGet(() -> getRequiredRootEntity(context));
        PreparedQuery<?, ?> preparedQuery = prepareQuery(context, idType);
        Flowable<Boolean> publisher = Flowable.fromPublisher(reactiveOperations.findOptional(preparedQuery))
                .map(o -> true)
                .switchIfEmpty(Flowable.just(false));
        return Publishers.convertPublisher(publisher, context.getReturnType().getType());
    }
}
