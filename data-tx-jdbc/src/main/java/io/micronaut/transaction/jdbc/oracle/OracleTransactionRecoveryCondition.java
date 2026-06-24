/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.transaction.jdbc.oracle;

import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.Qualifier;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.Named;
import io.micronaut.inject.BeanDefinition;

/**
 * Enables Oracle transaction recovery when the datasource is Oracle and the feature is configured.
 *
 * @since 5.1
 */
@Internal
final class OracleTransactionRecoveryCondition implements Condition {

    private static final String DATASOURCES = "datasources";
    private static final String DIALECT = "dialect";
    private static final String ORACLE_DIALECT = "ORACLE";
    private static final String ENABLE_RECOVERY = "enable-oracle-transaction-recovery";

    @Override
    public boolean matches(ConditionContext context) {
        BeanResolutionContext beanResolutionContext = context.getBeanResolutionContext();
        String dataSourceName;
        if (beanResolutionContext == null) {
            return true;
        }
        Qualifier<?> currentQualifier = beanResolutionContext.getCurrentQualifier();
        if (currentQualifier == null && context.getComponent() instanceof BeanDefinition<?> definition) {
            currentQualifier = definition.getDeclaredQualifier();
        }
        if (currentQualifier instanceof Named named) {
            dataSourceName = named.getName();
        } else {
            dataSourceName = "default";
        }

        String dialectProperty = DATASOURCES + '.' + dataSourceName + '.' + DIALECT;
        String dialect = context.getProperty(dialectProperty, String.class).orElse(null);
        if (!ORACLE_DIALECT.equalsIgnoreCase(dialect)) {
            return false;
        }

        String property = DATASOURCES + '.' + dataSourceName + '.' + ENABLE_RECOVERY;
        return context.getProperty(property, Boolean.class, false);
    }
}
