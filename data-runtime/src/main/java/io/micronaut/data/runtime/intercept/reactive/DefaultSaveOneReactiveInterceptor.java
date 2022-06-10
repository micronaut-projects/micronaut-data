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
package io.micronaut.data.runtime.intercept.reactive;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.intercept.reactive.SaveOneReactiveInterceptor;
import io.micronaut.data.operations.RepositoryOperations;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Default implementation of {@link SaveOneReactiveInterceptor}.
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultSaveOneReactiveInterceptor extends AbstractCountOrEntityPublisherInterceptor
        implements SaveOneReactiveInterceptor<Object, Object> {
    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    protected DefaultSaveOneReactiveInterceptor(@NonNull RepositoryOperations operations) {
        super(operations);
    }

    @Override
    public Publisher<?> interceptPublisher(RepositoryMethodKey methodKey, MethodInvocationContext<Object, Object> context) {
        Class<?> rootEntity = getRequiredRootEntity(context);
        Map<String, Object> parameterValueMap = getParameterValueMap(context);

        return Mono.fromCallable(() -> {
            Object o = instantiateEntity(rootEntity, parameterValueMap);
            return getInsertOperation(context, o);
        }).flatMapMany(reactiveOperations::persist);
    }
}
