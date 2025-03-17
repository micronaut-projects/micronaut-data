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
package io.micronaut.data.runtime.intercept;

import io.micronaut.aop.InterceptedMethod;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.intercept.DataInterceptor;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.support.NullValue;
import io.micronaut.inject.InjectionPoint;
import jakarta.inject.Inject;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 * The root Data introduction advice, which simply delegates to an appropriate interceptor
 * declared in the {@link io.micronaut.data.intercept} package.
 *
 * @author graemerocher
 * @since 1.0
 */
@InterceptorBean(Repository.class)
@Prototype
@Internal
public final class DataIntroductionAdvice implements MethodInterceptor<Object, Object> {

    private final DataInterceptorResolver dataInterceptorResolver;
    @Nullable
    private final InjectionPoint<?> injectionPoint;

    private final DataConversionService conversionService;

    /**
     * Default constructor.
     *
     * @param dataInterceptorResolver The data interceptor resolver
     * @param injectionPoint          The injection point
     * @param conversionService       The conversion service
     */
    @Inject
    public DataIntroductionAdvice(@NonNull DataInterceptorResolver dataInterceptorResolver,
                                  @Nullable InjectionPoint<?> injectionPoint,
                                  DataConversionService conversionService) {
        this.dataInterceptorResolver = dataInterceptorResolver;
        this.injectionPoint = injectionPoint;
        this.conversionService = conversionService;
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        RepositoryMethodKey key = new RepositoryMethodKey(context.getTarget(), context.getExecutableMethod());
        DataInterceptor<Object, Object> dataInterceptor = dataInterceptorResolver.resolve(key, context, injectionPoint);
        InterceptedMethod interceptedMethod = InterceptedMethod.of(context, conversionService);
        try {
            return switch (interceptedMethod.resultType()) {
                case PUBLISHER ->
                    interceptedMethod.handleResult(dataInterceptor.intercept(key, context));
                case COMPLETION_STAGE ->
                    interceptedMethod.handleResult(interceptCompletionStage(context, dataInterceptor, key));
                case SYNCHRONOUS -> dataInterceptor.intercept(key, context);
            };
        } catch (Exception e) {
            return interceptedMethod.handleException(e);
        }
    }

    private Object interceptCompletionStage(MethodInvocationContext<Object, Object> context,
                                            DataInterceptor<Object, Object> dataInterceptor,
                                            RepositoryMethodKey key) {
        PropagatedContext propagatedContext = PropagatedContext.getOrEmpty();
        CompletionStage<Object> completionStage = (CompletionStage<Object>) dataInterceptor.intercept(key, context);
        CompletableFuture<Object> completableFuture = new CompletableFuture<>();
        completionStage.whenComplete((value, throwable) -> {
            try (PropagatedContext.Scope ignore = propagatedContext.propagate()) {
                if (throwable == null) {
                    Class<Object> target = context.getReturnType().asArgument().getType();
                    if (value == null) {
                        value = conversionService.convert(new NullValue(), target).orElse(value);
                    } else {
                        value = conversionService.convert(value, target).orElse(value);
                    }
                    completableFuture.complete(value);
                } else {
                    Throwable finalThrowable = throwable;
                    if (finalThrowable instanceof CompletionException) {
                        finalThrowable = finalThrowable.getCause();
                    }
                    completableFuture.completeExceptionally(finalThrowable);
                }
            }
        });
        return completableFuture;
    }

}
