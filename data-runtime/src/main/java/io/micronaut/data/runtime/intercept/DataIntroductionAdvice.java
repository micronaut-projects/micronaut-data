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
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.type.MutableArgumentValue;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.data.annotation.OptimisticLockConflict;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.annotation.Version;
import io.micronaut.data.exceptions.OptimisticLockException;
import io.micronaut.data.exceptions.OptimisticLockExceptionHandler;
import io.micronaut.data.intercept.DataInterceptor;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.intercept.annotation.DataMethodQueryParameter;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.support.NullValue;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.InjectionPoint;
import jakarta.inject.Inject;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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

    private final BeanContext beanContext;
    private final DataConversionService conversionService;
    private final OptimisticLockConflictPolicyResolver optimisticLockConflictPolicyResolver;

    /**
     * Default constructor.
     *
     * @param dataInterceptorResolver The data interceptor resolver
     * @param injectionPoint          The injection point
     * @param beanContext             The bean context
     * @param conversionService       The conversion service
     * @param optimisticLockExceptionHandler The optimistic lock exception handler
     */
    @Inject
    public DataIntroductionAdvice(@NonNull DataInterceptorResolver dataInterceptorResolver,
                                  @Nullable InjectionPoint<?> injectionPoint,
                                  BeanContext beanContext,
                                  DataConversionService conversionService,
                                  @Nullable OptimisticLockExceptionHandler optimisticLockExceptionHandler) {
        this.dataInterceptorResolver = dataInterceptorResolver;
        this.injectionPoint = injectionPoint;
        this.beanContext = beanContext;
        this.conversionService = conversionService;
        this.optimisticLockConflictPolicyResolver = new OptimisticLockConflictPolicyResolver(optimisticLockExceptionHandler);
    }

    @Nullable
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
        } catch (OptimisticLockException e) {
            try {
                return interceptedMethod.handleResult(handleOptimisticLockConflict(context, dataInterceptor, key, e));
            } catch (Exception ex) {
                return interceptedMethod.handleException(ex);
            }
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
        Objects.requireNonNull(completionStage).whenComplete((val, throwable) -> {
            propagatedContext.propagate(() -> {
                if (throwable == null) {
                    completeResult(context, completableFuture, val);
                } else {
                    Throwable finalThrowable = throwable;
                    if (finalThrowable instanceof CompletionException) {
                        finalThrowable = finalThrowable.getCause();
                    }
                    if (finalThrowable instanceof OptimisticLockException optimisticLockException) {
                        try {
                            completeResult(context, completableFuture,
                                handleOptimisticLockConflict(context, dataInterceptor, key, optimisticLockException));
                        } catch (Exception e) {
                            completableFuture.completeExceptionally(e);
                        }
                    } else {
                        completableFuture.completeExceptionally(finalThrowable);
                    }
                }
                return null;
            });
        });
        return completableFuture;
    }

    private void completeResult(MethodInvocationContext<Object, Object> context,
                                CompletableFuture<Object> completableFuture,
                                @Nullable Object value) {
        Class<Object> target = context.getReturnType().asArgument().getType();
        Object v = value;
        if (v == null && target.getName().equals("kotlinx.coroutines.flow.Flow")) {
            v = conversionService.convert(new NullValue(), target).orElse(v);
        } else {
            v = conversionService.convert(v, target).orElse(v);
        }
        completableFuture.complete(v);
    }

    @Nullable
    private Object handleOptimisticLockConflict(MethodInvocationContext<Object, Object> context,
                                                DataInterceptor<Object, Object> dataInterceptor,
                                                RepositoryMethodKey key,
                                                OptimisticLockException exception) {
        OptimisticLockConflict.Policy policy = optimisticLockConflictPolicyResolver.resolvePolicy(context);
        return switch (policy) {
            case FAIL_FAST -> throw exception;
            case DELEGATE -> optimisticLockConflictPolicyResolver.resolveDelegate(context, exception);
            case RELOAD_AND_RETRY -> reloadAndRetry(context, dataInterceptor, key, exception);
        };
    }

    @Nullable
    private Object reloadAndRetry(MethodInvocationContext<Object, Object> context,
                                  DataInterceptor<Object, Object> dataInterceptor,
                                  RepositoryMethodKey key,
                                  OptimisticLockException initialException) {
        int maxRetries = context.intValue(DataMethod.class, DataMethod.META_MEMBER_OPTIMISTIC_LOCK_CONFLICT_MAX_RETRIES).orElse(1);
        if (maxRetries < 1) {
            throw new IllegalStateException("@OptimisticLockConflict maxRetries must be greater than 0.", initialException);
        }
        OptimisticLockException lastException = initialException;
        for (int i = 0; i < maxRetries; i++) {
            refreshRetryArguments(context, lastException);
            try {
                return dataInterceptor.intercept(key, context);
            } catch (OptimisticLockException e) {
                lastException = e;
            }
        }
        throw lastException;
    }

    private void refreshRetryArguments(MethodInvocationContext<Object, Object> context,
                                       OptimisticLockException exception) {
        Optional<String> entityParameterName = context.stringValue(DataMethod.NAME, TypeRole.ENTITY);
        if (entityParameterName.isPresent()) {
            refreshEntityArgumentVersion(context, entityParameterName.get(), exception);
            return;
        }
        refreshVersionArgument(context, exception);
    }

    private void refreshEntityArgumentVersion(MethodInvocationContext<Object, Object> context,
                                              String entityParameterName,
                                              OptimisticLockException exception) {
        MutableArgumentValue<?> mutableArgumentValue = context.getParameters().get(entityParameterName);
        if (mutableArgumentValue == null) {
            throw new IllegalStateException("Cannot locate entity parameter in method context.", exception);
        }
        Object entityArgument = mutableArgumentValue.getValue();
        if (entityArgument == null) {
            throw new IllegalStateException("Entity parameter cannot be null for RELOAD_AND_RETRY policy.", exception);
        }
        Object idValue = resolveIdValue(entityArgument, exception);
        Object currentEntity = findCurrentEntity(context, idValue, exception);
        Object latestVersion = resolveVersionValue(currentEntity, exception);
        setVersionValue(entityArgument, latestVersion, exception);
    }

    private void refreshVersionArgument(MethodInvocationContext<Object, Object> context,
                                        OptimisticLockException exception) {
        int idParameterIndex = getParameterIndex(context, "id");
        int versionParameterIndex = getParameterIndex(context, "version");
        Object idValue = context.getParameterValues()[idParameterIndex];
        Object currentEntity = findCurrentEntity(context, idValue, exception);
        Object latestVersion = resolveVersionValue(currentEntity, exception);
        Object[] parameterValues = context.getParameterValues();
        parameterValues[versionParameterIndex] = latestVersion;
        String versionParameterName = context.getArguments()[versionParameterIndex].getName();
        MutableArgumentValue<?> mutableArgumentValue = context.getParameters().get(versionParameterName);
        if (mutableArgumentValue != null) {
            MutableArgumentValue<Object> mutableObjectArgument = (MutableArgumentValue<Object>) mutableArgumentValue;
            mutableObjectArgument.setValue(latestVersion);
        }
    }

    private int getParameterIndex(MethodInvocationContext<Object, Object> context, String propertyName) {
        AnnotationValue<Annotation> dataMethod = context.getAnnotation(DataMethod.NAME);
        if (dataMethod == null) {
            throw new IllegalStateException("No @DataMethod metadata present.");
        }
        List<AnnotationValue<Annotation>> parameters = dataMethod.getAnnotations(DataMethod.META_MEMBER_PARAMETERS);
        for (AnnotationValue<Annotation> parameter : parameters) {
            int parameterIndex = parameter.intValue(DataMethodQueryParameter.META_MEMBER_PARAMETER_INDEX).orElse(-1);
            if (parameterIndex < 0) {
                continue;
            }
            Optional<String> property = parameter.stringValue(DataMethodQueryParameter.META_MEMBER_PROPERTY);
            if (propertyName.equals(property.orElse(null))) {
                return parameterIndex;
            }
        }
        throw new IllegalStateException("Cannot locate parameter for property: " + propertyName);
    }

    private Object findCurrentEntity(MethodInvocationContext<Object, Object> context,
                                     Object idValue,
                                     OptimisticLockException exception) {
        BeanDefinition<Object> beanDefinition = beanContext.findBeanDefinition(context.getDeclaringType())
            .orElseThrow(() -> new IllegalStateException("Cannot locate repository bean definition for RELOAD_AND_RETRY policy.", exception));
        ExecutableMethod<Object, Object> findByIdMethod = findByIdMethod(beanDefinition)
            .orElseThrow(() -> new IllegalStateException("RELOAD_AND_RETRY policy requires findById(ID) method on repository.", exception));
        Object result = findByIdMethod.invoke(context.getTarget(), idValue);
        if (result instanceof Optional<?> optionalEntity) {
            return optionalEntity.orElseThrow(() -> exception);
        }
        throw new IllegalStateException("findById(ID) must return Optional for RELOAD_AND_RETRY policy.", exception);
    }

    private Optional<ExecutableMethod<Object, Object>> findByIdMethod(BeanDefinition<Object> beanDefinition) {
        return beanDefinition.findPossibleMethods("findById")
            .filter(method -> method.getArguments().length == 1)
            .findFirst();
    }

    private Object resolveVersionValue(Object entity, OptimisticLockException exception) {
        BeanIntrospection<Object> introspection = (BeanIntrospection<Object>) BeanIntrospection.getIntrospection(entity.getClass());
        for (BeanProperty<Object, Object> beanProperty : introspection.getBeanProperties()) {
            if (beanProperty.hasAnnotation(Version.class)) {
                Object value = beanProperty.get(entity);
                if (value == null) {
                    throw new IllegalStateException("Version value cannot be null for RELOAD_AND_RETRY policy.", exception);
                }
                return value;
            }
        }
        throw new IllegalStateException("No @Version property found on entity for RELOAD_AND_RETRY policy.", exception);
    }

    private Object resolveIdValue(Object entity, OptimisticLockException exception) {
        BeanIntrospection<Object> introspection = (BeanIntrospection<Object>) BeanIntrospection.getIntrospection(entity.getClass());
        for (BeanProperty<Object, Object> beanProperty : introspection.getBeanProperties()) {
            if (beanProperty.hasAnnotation(io.micronaut.data.annotation.Id.class)) {
                Object value = beanProperty.get(entity);
                if (value == null) {
                    throw new IllegalStateException("Entity id cannot be null for RELOAD_AND_RETRY policy.", exception);
                }
                return value;
            }
        }
        throw new IllegalStateException("No @Id property found on entity for RELOAD_AND_RETRY policy.", exception);
    }

    private void setVersionValue(Object entity,
                                 Object latestVersion,
                                 OptimisticLockException exception) {
        BeanIntrospection<Object> introspection = (BeanIntrospection<Object>) BeanIntrospection.getIntrospection(entity.getClass());
        for (BeanProperty<Object, Object> beanProperty : introspection.getBeanProperties()) {
            if (beanProperty.hasAnnotation(Version.class)) {
                beanProperty.set(entity, latestVersion);
                return;
            }
        }
        throw new IllegalStateException("No @Version property found on entity for RELOAD_AND_RETRY policy.", exception);
    }

}
