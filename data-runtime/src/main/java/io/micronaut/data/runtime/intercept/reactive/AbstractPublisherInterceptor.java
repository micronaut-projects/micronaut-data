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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.type.Argument;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.operations.RepositoryOperations;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.util.Optional;

/**
 * Publisher interceptor.
 *
 * @author Denis Stepanov
 * @since 3.5.0
 */
@Internal
public abstract class AbstractPublisherInterceptor extends AbstractReactiveInterceptor<Object, Object> {

    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    protected AbstractPublisherInterceptor(@NonNull RepositoryOperations operations) {
        super(operations);
    }

    /**
     * Intercept publisher.
     *
     * @param methodKey The method key
     * @param context   The context
     * @return the result publisher
     */
    protected abstract Publisher<?> interceptPublisher(RepositoryMethodKey methodKey, MethodInvocationContext<Object, Object> context);

    @Override
    public final Object intercept(RepositoryMethodKey methodKey, MethodInvocationContext<Object, Object> context) {
        Publisher<Object> publisher = (Publisher<Object>) interceptPublisher(methodKey, context);
        Optional<Argument<?>> optionalArgument = context.getReturnType().getFirstTypeVariable();
        if (optionalArgument.isPresent()) {
            Argument<?> argument = optionalArgument.get();
            if (!argument.isVoid() && argument.getType() != Void.class) {
                publisher = Flux.from(publisher).map(result -> convertOne(result, argument));
            }
        }
        return Publishers.convertPublisher(conversionService, publisher, context.getReturnType().getType());
    }
}
