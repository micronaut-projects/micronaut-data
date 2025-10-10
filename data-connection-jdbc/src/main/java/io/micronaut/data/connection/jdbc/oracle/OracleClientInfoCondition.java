/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.data.connection.jdbc.oracle;

import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.Qualifier;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.Named;
import io.micronaut.inject.BeanDefinition;

/**
 * A condition that determines whether to customize Oracle client information based on configuration properties.
 *
 * This condition checks if the data source dialect is set to Oracle and if the 'customize-oracle-client-info' property is enabled.
 *
 * @author radovanradic
 * @since 4.11
 */
@Internal
final class OracleClientInfoCondition implements Condition {

    static final String DATASOURCES = "datasources";
    private static final Character DOT = '.';
    private static final String DIALECT = "dialect";
    private static final String ORACLE_CLIENT_INFO_ENABLED = "enable-oracle-client-info";
    private static final String ORACLE_DIALECT = "ORACLE";

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

        String dialectProperty = DATASOURCES + DOT + dataSourceName + DOT + DIALECT;
        String dialect = context.getProperty(dialectProperty, String.class).orElse(null);
        if (!ORACLE_DIALECT.equalsIgnoreCase(dialect)) {
            return false;
        }

        String property = DATASOURCES + DOT + dataSourceName + DOT + ORACLE_CLIENT_INFO_ENABLED;
        return context.getProperty(property, Boolean.class, false);
    }
}
