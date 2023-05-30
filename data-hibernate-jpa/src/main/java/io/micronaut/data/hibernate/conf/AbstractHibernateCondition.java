/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.data.hibernate.conf;

import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.Qualifier;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.Named;
import io.micronaut.inject.BeanDefinition;

/**
 * Abstract transaction manager condition.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
abstract sealed class AbstractHibernateCondition implements Condition permits HibernateReactiveCondition, HibernateSyncCondition {

    /**
     * @return true if reactive is required
     */
    protected abstract boolean isReactive();

    @Override
    public boolean matches(ConditionContext context) {
        BeanResolutionContext beanResolutionContext = context.getBeanResolutionContext();
        String dataSourceName;
        if (beanResolutionContext == null) {
           return true;
        } else {
            Qualifier<?> currentQualifier = beanResolutionContext.getCurrentQualifier();
            if (currentQualifier == null && context.getComponent() instanceof BeanDefinition<?> definition) {
                currentQualifier = definition.getDeclaredQualifier();
            }
            if (currentQualifier instanceof Named named) {
                dataSourceName = named.getName();
            } else {
                dataSourceName = "default";
            }
        }
        return context.getProperty("jpa." + dataSourceName + ".reactive", Boolean.class, false) == isReactive();
    }
}
