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
package io.micronaut.data.jdbc.operations;

import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.Qualifier;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.Named;
import io.micronaut.inject.BeanDefinition;

/**
 * Condition that enables the default JDBC repository operations for non-Oracle datasources.
 */
@Internal
final class DefaultJdbcRepositoryOperationsCondition implements Condition {

    /**
     * Checks whether the current datasource is not configured with the Oracle dialect.
     *
     * @param context The condition context
     * @return {@code true} when default JDBC operations should be enabled
     */
    @Override
    public boolean matches(ConditionContext context) {
        return !JdbcRepositoryOperationsConditions.isOracleDialect(context);
    }
}

/**
 * Condition that enables Oracle-specific JDBC repository operations for Oracle datasources.
 */
@Internal
final class OracleJdbcRepositoryOperationsCondition implements Condition {

    /**
     * Checks whether the current datasource is configured with the Oracle dialect.
     *
     * @param context The condition context
     * @return {@code true} when Oracle JDBC operations should be enabled
     */
    @Override
    public boolean matches(ConditionContext context) {
        return JdbcRepositoryOperationsConditions.isOracleDialect(context);
    }
}

/**
 * Shared condition utilities for selecting the JDBC repository operations bean.
 */
@Internal
final class JdbcRepositoryOperationsConditions {

    private static final String DATASOURCES = "datasources";
    private static final String DIALECT = "dialect";
    private static final String ORACLE_DIALECT = "ORACLE";
    private static final String DEFAULT = "default";

    private JdbcRepositoryOperationsConditions() {
    }

    /**
     * Checks whether the datasource associated with the current bean resolution uses the Oracle dialect.
     *
     * @param context The condition context
     * @return {@code true} when the datasource is configured with {@code datasources.<name>.dialect=ORACLE}
     */
    static boolean isOracleDialect(ConditionContext context) {
        String dataSourceName = resolveDataSourceName(context);
        String dialectProperty = DATASOURCES + '.' + dataSourceName + '.' + DIALECT;
        String dialect = context.getProperty(dialectProperty, String.class).orElse(null);
        return ORACLE_DIALECT.equalsIgnoreCase(dialect);
    }

    /**
     * Resolves the datasource name from the current qualifier, falling back to {@code default}.
     *
     * @param context The condition context
     * @return The datasource name
     */
    private static String resolveDataSourceName(ConditionContext context) {
        BeanResolutionContext beanResolutionContext = context.getBeanResolutionContext();
        Qualifier<?> currentQualifier = null;
        if (beanResolutionContext != null) {
            currentQualifier = beanResolutionContext.getCurrentQualifier();
        }
        if (currentQualifier == null && context.getComponent() instanceof BeanDefinition<?> definition) {
            currentQualifier = definition.getDeclaredQualifier();
        }
        if (currentQualifier instanceof Named named) {
            return named.getName();
        }
        return DEFAULT;
    }
}
