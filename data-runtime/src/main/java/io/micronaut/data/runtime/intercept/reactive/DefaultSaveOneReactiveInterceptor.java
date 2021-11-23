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
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.intercept.reactive.SaveOneReactiveInterceptor;
import io.micronaut.data.operations.RepositoryOperations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Default implementation of {@link SaveOneReactiveInterceptor}.
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultSaveOneReactiveInterceptor extends AbstractReactiveInterceptor<Object, Object>
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
    public Object intercept(RepositoryMethodKey methodKey, MethodInvocationContext<Object, Object> context) {
        Class<?> rootEntity = getRequiredRootEntity(context);
        Map<String, Object> parameterValueMap = getParameterValueMap(context);

        Flux<Object> publisher = Mono.fromCallable(() -> {
            Object o = instantiateEntity(rootEntity, parameterValueMap);
            return getInsertOperation(context, o);
        }).flatMapMany(reactiveOperations::persist);
        ReturnType<Object> rt = context.getReturnType();
        Argument<?> reactiveValue = context.getReturnType().asArgument().getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
        if (isNumber(reactiveValue.getType())) {
            return operations.getConversionService().convert(count(publisher), rt.getType())
                    .orElseThrow(() -> new IllegalStateException("Unsupported return type: " + rt.getType()));
        }
        return Publishers.convertPublisher(publisher, context.getReturnType().getType());
    }
}
