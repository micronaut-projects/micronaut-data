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
package io.micronaut.transaction.support;

import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.Qualifier;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.naming.Named;

import java.util.Optional;

/**
 * Abstract transaction manager condition.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
public abstract class AbstractDataSourceTransactionManagerCondition implements Condition {

    protected abstract String getTransactionManagerName();

    @Override
    public boolean matches(ConditionContext context) {
        BeanResolutionContext beanResolutionContext = context.getBeanResolutionContext();
        String dataSourceName;
        if (beanResolutionContext == null) {
            dataSourceName = "default";
        } else {
            Qualifier<?> currentQualifier = beanResolutionContext.getCurrentQualifier();
            if (currentQualifier instanceof Named named) {
                dataSourceName = named.getName();
            } else {
                dataSourceName = "default";
            }
        }
        // and do what now? transactionManagerProperty(ConditionContext, String) deprecated and removed
        return transactionManagerProperty(context, dataSourceName)
            .map(name -> name.equals(getTransactionManagerName()))
            .orElse(true);
    }

    @Deprecated
    @NonNull
    private Optional<String> transactionManagerProperty(@NonNull ConditionContext context,
                                                        @NonNull String dataSourceName) {
        String propertyName = "datasources." + dataSourceName + ".transactionManager";
        Optional<String> property = context.getProperty(propertyName, String.class);
        if (property.isPresent()) {
            return property;
        }
        String lowerKebapCasePropertyName = "datasources." + dataSourceName + ".transaction-manager";
        return context.getProperty(lowerKebapCasePropertyName, String.class);
    }
}
