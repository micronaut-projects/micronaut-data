/*
 * Copyright 2017-2019 original authors
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
import io.micronaut.data.annotation.RepositoryConfiguration;
import io.micronaut.data.operations.PrimaryRepositoryOperations;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.inject.qualifiers.Qualifiers;

import javax.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The root Data introduction advice, which simply delegates to an appropriate interceptor
 * declared in the {@link io.micronaut.data.intercept} package.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Internal
public final class DataIntroductionAdvice implements MethodInterceptor<Object, Object> {

    private final BeanLocator beanLocator;
    private final Map<RepositoryMethodKey, DataInterceptor> interceptorMap = new ConcurrentHashMap<>(20);

    /**
     * Default constructor.
     * @param beanLocator The bean locator
     */
    DataIntroductionAdvice(BeanLocator beanLocator) {
        this.beanLocator = beanLocator;
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        RepositoryMethodKey key = new RepositoryMethodKey(context.getTarget(), context.getExecutableMethod());
        DataInterceptor<Object, Object> dataInterceptor = interceptorMap.get(key);
        if (dataInterceptor == null) {
            String dataSourceName = context.stringValue(Repository.class).orElse(null);
            Class<?> operationsType = context.classValue(RepositoryConfiguration.class, "operations")
                    .orElse(PrimaryRepositoryOperations.class);
            Class<?> interceptorType = context
                    .classValue(DataMethod.class, DataMethod.META_MEMBER_INTERCEPTOR)
                    .orElse(null);

            if (interceptorType != null && DataInterceptor.class.isAssignableFrom(interceptorType)) {
                DataInterceptor<Object, Object> childInterceptor =
                        findInterceptor(dataSourceName, operationsType, interceptorType);
                interceptorMap.put(key, childInterceptor);
                return childInterceptor.intercept(key, context);

            } else {
                return context.proceed();
            }
        } else {
            return dataInterceptor.intercept(key, context);
        }

    }

    private @NonNull
    DataInterceptor<Object, Object> findInterceptor(
            @Nullable String dataSourceName,
            @NonNull Class<?> operationsType,
            @NonNull Class<?> interceptorType) {
        DataInterceptor interceptor;
        if (!RepositoryOperations.class.isAssignableFrom(operationsType)) {
            throw new IllegalArgumentException("Repository type must be an instance of RepositoryOperations!");
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
            throw new ConfigurationException("No backing RepositoryOperations configured for repository. Check your configuration and try again", e);
        }
        BeanIntrospection<Object> introspection = BeanIntrospector.SHARED.findIntrospections(ref -> interceptorType.isAssignableFrom(ref.getBeanType())).stream().findFirst().orElseThrow(() ->
                new DataAccessException("No Data interceptor found for type: " + interceptorType)
        );
        if (introspection.getConstructorArguments().length == 0) {
            interceptor = (DataInterceptor) introspection.instantiate();
        } else {
            interceptor = (DataInterceptor) introspection.instantiate(datastore);
        }
        return interceptor;
    }

}
