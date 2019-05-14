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

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.BeanLocator;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.intercept.annotation.PredatorMethod;
import io.micronaut.inject.qualifiers.Qualifiers;

import javax.inject.Singleton;

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

    /**
     * Default constructor.
     * @param beanLocator The bean locator
     */
    protected PredatorIntroductionAdvice(BeanLocator beanLocator) {
        this.beanLocator = beanLocator;
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        String dataSourceName = context.getValue(Repository.class, String.class).orElse(null);
        Class<?> interceptorType = context
                .getValue(PredatorMethod.class, "interceptor", Class.class)
                .orElse(null);

        if (interceptorType != null && PredatorInterceptor.class.isAssignableFrom(interceptorType)) {
            PredatorInterceptor<Object, Object> childInterceptor;

            if (dataSourceName != null) {
                childInterceptor = (PredatorInterceptor) beanLocator.getBean(interceptorType, Qualifiers.byName(dataSourceName));
            } else {
                childInterceptor = (PredatorInterceptor) beanLocator.getBean(interceptorType);
            }

            return childInterceptor.intercept(context);

        } else {
            return context.proceed();
        }
    }
}
