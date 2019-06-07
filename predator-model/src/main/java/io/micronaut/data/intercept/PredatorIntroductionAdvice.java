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
package io.micronaut.data.intercept;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.BeanLocator;
import io.micronaut.context.Qualifier;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.intercept.annotation.PredatorMethod;
import io.micronaut.inject.qualifiers.Qualifiers;

import javax.inject.Singleton;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The root Predator introduction advice, which simply delegates to an appropriate interceptor
 * declared in the {@link io.micronaut.data.intercept} package.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Internal
public class PredatorIntroductionAdvice implements MethodInterceptor<Object, Object> {

    private final BeanLocator beanLocator;
    private final Map<InterceptorKey, PredatorInterceptor> interceptorMap = new ConcurrentHashMap<>(20);

    /**
     * Default constructor.
     * @param beanLocator The bean locator
     */
    protected PredatorIntroductionAdvice(BeanLocator beanLocator) {
        this.beanLocator = beanLocator;
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        String dataSourceName = context.stringValue(Repository.class).orElse(null);
        Class<?> operationsType = context.classValue(Repository.class, "operations")
                                      .orElse(RepositoryOperations.class);
        Class<?> interceptorType = context
                .classValue(PredatorMethod.class, PredatorMethod.META_MEMBER_INTERCEPTOR)
                .orElse(null);

        if (interceptorType != null && PredatorInterceptor.class.isAssignableFrom(interceptorType)) {
            PredatorInterceptor<Object, Object> childInterceptor =
                    findInterceptor(dataSourceName, operationsType, interceptorType);
            return childInterceptor.intercept(context);

        } else {
            return context.proceed();
        }
    }

    private @NonNull PredatorInterceptor<Object, Object> findInterceptor(
            @Nullable String dataSourceName,
            @NonNull Class<?> operationsType,
            @NonNull Class<?> interceptorType) {
        InterceptorKey key = new InterceptorKey(dataSourceName, operationsType, interceptorType);
        PredatorInterceptor interceptor = interceptorMap.get(key);
        if (interceptor == null) {
            if (!RepositoryOperations.class.isAssignableFrom(operationsType)) {
                throw new IllegalArgumentException("Backend type must be an instance of Datastore!");
            }

            RepositoryOperations datastore;
            try {
                if (dataSourceName != null) {
                    Qualifier qualifier = Qualifiers.byName(dataSourceName);
                    datastore = (RepositoryOperations) beanLocator.getBean(operationsType, qualifier);
                } else {
                    datastore = (RepositoryOperations) beanLocator.getBean(operationsType);
                }
            } catch (NoSuchBeanException e) {
                throw new ConfigurationException("No backing datasource configured for repository. Check your configuration and try again", e);
            }
            BeanIntrospection<Object> introspection = BeanIntrospector.SHARED.findIntrospections(ref -> interceptorType.isAssignableFrom(ref.getBeanType())).stream().findFirst().orElseThrow(() ->
                    new DataAccessException("No Predator interceptor found for type: " + interceptorType)
            );
            if (introspection.getConstructorArguments().length == 0) {
                interceptor = (PredatorInterceptor) introspection.instantiate();
            } else {
                interceptor = (PredatorInterceptor) introspection.instantiate(datastore);
            }
            interceptorMap.put(key, interceptor);
        }
        return interceptor;
    }

    /**
     * Key used to cache the target child interceptor.
     */
    private final class InterceptorKey {
        final String dataSource;
        final Class<?> operationsType;
        final Class<?> interceptorType;

        InterceptorKey(String dataSource, Class<?> operationsType, Class<?> interceptorType) {
            this.dataSource = dataSource;
            this.operationsType = operationsType;
            this.interceptorType = interceptorType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            InterceptorKey that = (InterceptorKey) o;
            return Objects.equals(dataSource, that.dataSource) &&
                    operationsType.equals(that.operationsType) &&
                    interceptorType.equals(that.interceptorType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(dataSource, operationsType, interceptorType);
        }
    }
}
