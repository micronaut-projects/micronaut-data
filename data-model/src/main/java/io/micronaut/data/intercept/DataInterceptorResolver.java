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
package io.micronaut.data.intercept;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.BeanLocator;
import io.micronaut.context.Qualifier;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.RepositoryConfiguration;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.operations.PrimaryRepositoryOperations;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The data interceptor resolver.
 *
 * @author Denis Stepanov
 * @since 3.5.0
 */
@Internal
@Singleton
final class DataInterceptorResolver {

    private final BeanLocator locator;
    private final Map<RepositoryMethodKey, DataInterceptor<? super Object, ? super Object>> interceptors = new ConcurrentHashMap<>();

    DataInterceptorResolver(BeanLocator locator) {
        this.locator = locator;
    }

    DataInterceptor<Object, Object> resolve(@NonNull RepositoryMethodKey key,
                                            @NonNull MethodInvocationContext<Object, Object> context,
                                            @Nullable InjectionPoint<?> injectionPoint) {
        return interceptors.computeIfAbsent(key, (k) -> {
            final String dataSourceName = context.stringValue(Repository.class)
                .orElseGet(() -> injectionPoint == null ? null : injectionPoint.getAnnotationMetadata().stringValue(Repository.class).orElse(null));
            final Class<?> operationsType = context.classValue(RepositoryConfiguration.class, "operations")
                .orElse(PrimaryRepositoryOperations.class);
            final Class<?> interceptorType = context
                .classValue(DataMethod.class, DataMethod.META_MEMBER_INTERCEPTOR)
                .orElseGet(() -> {
                    final AnnotationValue<DataMethod> declaredAnnotation = context.getDeclaredAnnotation(DataMethod.class);
                    if (declaredAnnotation == null) {
                        return null;
                    }
                    return declaredAnnotation.classValue(DataMethod.META_MEMBER_INTERCEPTOR).orElse(null);
                });

            if (interceptorType != null && DataInterceptor.class.isAssignableFrom(interceptorType)) {
                return findInterceptor(dataSourceName, operationsType, interceptorType);
            }

            final String interceptorName = context.getAnnotationMetadata().stringValue(DataMethod.class, DataMethod.META_MEMBER_INTERCEPTOR).orElse(null);
            if (interceptorName != null) {
                throw new IllegalStateException("Micronaut Data Interceptor [" + interceptorName + "] is not on the classpath but required by the method: " + context.getExecutableMethod().toString());
            }
            throw new IllegalStateException("Micronaut Data method is missing compilation time query information. Ensure that the Micronaut Data annotation processors are declared in your build and try again with a clean re-build.");
        });
    }

    @NonNull
    private DataInterceptor<Object, Object> findInterceptor(@Nullable String dataSourceName,
                                                            @NonNull Class<?> operationsType,
                                                            @NonNull Class<?> interceptorType) {
        if (!RepositoryOperations.class.isAssignableFrom(operationsType)) {
            throw new IllegalArgumentException("Repository type must be an instance of RepositoryOperations!");
        }

        final RepositoryOperations datastore;
        try {
            if (dataSourceName != null) {
                final Qualifier qualifier = Qualifiers.byName(dataSourceName);
                datastore = (RepositoryOperations) locator.getBean(operationsType, qualifier);
            } else {
                datastore = (RepositoryOperations) locator.getBean(operationsType);
            }
        } catch (NoSuchBeanException e) {
            throw new ConfigurationException("No backing RepositoryOperations configured for repository. Check your configuration and try again", e);
        }
        final BeanIntrospection<Object> introspection = BeanIntrospector.SHARED.findIntrospections(ref ->
            ref.isPresent() && interceptorType.isAssignableFrom(ref.getBeanType())).stream().findFirst().orElseThrow(() ->
            new DataAccessException("No Data interceptor found for type: " + interceptorType)
        );

        final DataInterceptor interceptor;
        if (introspection.getConstructorArguments().length == 0) {
            interceptor = (DataInterceptor) introspection.instantiate();
        } else {
            interceptor = (DataInterceptor) introspection.instantiate(datastore);
        }
        return interceptor;
    }

}
