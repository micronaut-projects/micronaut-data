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

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.type.Argument;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.RepositoryConfiguration;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.intercept.DataInterceptor;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.operations.PrimaryRepositoryOperations;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.operations.RepositoryOperationsRegistry;
import io.micronaut.data.runtime.multitenancy.DataSourceTenantResolver;
import io.micronaut.inject.ArgumentInjectionPoint;
import io.micronaut.inject.InjectionPoint;
import jakarta.inject.Singleton;

import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The data interceptor resolver.
 *
 * @author Denis Stepanov
 * @since 3.5.0
 */
@Internal
@Singleton
public final class DataInterceptorResolver {

    private final RepositoryOperationsRegistry repositoryOperationsRegistry;
    @Nullable
    private final DataSourceTenantResolver tenantResolver;
    private final Map<TenantRepositoryMethodKey, DataInterceptor<? super Object, ? super Object>> interceptors = new ConcurrentHashMap<>();

    DataInterceptorResolver(RepositoryOperationsRegistry repositoryOperationsRegistry, @Nullable DataSourceTenantResolver tenantResolver) {
        this.repositoryOperationsRegistry = repositoryOperationsRegistry;
        this.tenantResolver = tenantResolver;
    }

    DataInterceptor<Object, Object> resolve(@NonNull RepositoryMethodKey key,
                                            @NonNull MethodInvocationContext<Object, Object> context,
                                            @Nullable InjectionPoint<?> injectionPoint) {
        String tenantDataSourceName;
        if (tenantResolver != null) {
            tenantDataSourceName = tenantResolver.resolveTenantDataSourceName();
        } else {
            tenantDataSourceName = null;
        }
        TenantRepositoryMethodKey theKey = new TenantRepositoryMethodKey(tenantDataSourceName, key, null);
        if (tenantDataSourceName == null && injectionPoint instanceof ArgumentInjectionPoint<?, ?> argumentInjectionPoint) {
            theKey = new TenantRepositoryMethodKey(tenantDataSourceName, key, argumentInjectionPoint.asArgument());
        }
        // Don't use "computeIfAbsent" to avoid "java.lang.IllegalStateException: Recursive update"
        DataInterceptor<? super Object, ? super Object> dataInterceptor = interceptors.get(theKey);
        if (dataInterceptor == null) {
            dataInterceptor = findDataInterceptor(context, injectionPoint, tenantDataSourceName);
            interceptors.put(theKey, dataInterceptor);
        }
        return dataInterceptor;
    }

    private DataInterceptor<Object, Object> findDataInterceptor(MethodInvocationContext<Object, Object> context, InjectionPoint<?> injectionPoint, String tenantDataSourceName) {
        final String dataSourceName;
        if (tenantDataSourceName == null) {
            dataSourceName = context.stringValue(Repository.class)
                .orElseGet(() -> injectionPoint == null ? null : injectionPoint.getAnnotationMetadata().stringValue(Repository.class).orElse(null));
        } else {
            dataSourceName = tenantDataSourceName;
        }
        final Class<? extends RepositoryOperations> operationsType = context.classValue(RepositoryConfiguration.class, "operations")
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
            throw new IllegalStateException("Micronaut Data Interceptor [" + interceptorName + "] is not on the classpath but required by the method: " + context.getExecutableMethod());
        }
        throw new IllegalStateException("Micronaut Data method is missing compilation time query information. Ensure that the Micronaut Data annotation processors are declared in your build and try again with a clean re-build.");
    }

    @NonNull
    private DataInterceptor<Object, Object> findInterceptor(@Nullable String dataSourceName,
                                                            @NonNull Class<? extends RepositoryOperations> operationsType,
                                                            @NonNull Class<?> interceptorType) {
        if (!RepositoryOperations.class.isAssignableFrom(operationsType)) {
            throw new IllegalArgumentException("Repository type must be an instance of RepositoryOperations!");
        }

        final RepositoryOperations datastore = repositoryOperationsRegistry.provide(operationsType, dataSourceName);
        Collection<BeanIntrospection<Object>> candidates = BeanIntrospector.SHARED.findIntrospections(ref -> {
            if (ref.isPresent()) {
                Class<?> beanType = ref.getBeanType();
                return interceptorType.isAssignableFrom(beanType) && !Modifier.isAbstract(beanType.getModifiers());
            }
            return false;
        });
        final BeanIntrospection<Object> introspection = candidates.stream().findFirst().orElseThrow(() ->
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

    private static final class TenantRepositoryMethodKey {
        private final String dataSource;
        private final RepositoryMethodKey key;
        private final Argument<?> injectionPoint;
        private final int hashCode;

        TenantRepositoryMethodKey(String dataSource, RepositoryMethodKey key, @Nullable Argument<?> injectionPoint) {
            this.dataSource = dataSource;
            this.key = key;
            this.injectionPoint = injectionPoint;
            this.hashCode = Objects.hash(dataSource, key);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TenantRepositoryMethodKey that = (TenantRepositoryMethodKey) o;
            return Objects.equals(dataSource, that.dataSource) && key.equals(that.key) && Objects.equals(injectionPoint, that.injectionPoint);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

}
