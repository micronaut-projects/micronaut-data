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
package io.micronaut.data.r2dbc.operations;

import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.runtime.support.DataSourceConfigurationUtils;
import io.micronaut.data.r2dbc.config.DataR2dbcConfiguration;

import java.util.List;
import java.util.Optional;

/**
 * Condition that enables the default R2DBC repository operations for datasources without a specialized implementation.
 */
@Internal
final class DefaultR2dbcRepositoryOperationsCondition implements Condition {

    /**
     * Checks whether the current datasource is not configured with a dialect that has specialized R2DBC operations.
     *
     * @param context The condition context
     * @return {@code true} when default R2DBC operations should be enabled
     */
    @Override
    public boolean matches(ConditionContext context) {
        return R2dbcRepositoryOperationsConditions.isDefaultOperationsDialect(context);
    }
}

/**
 * Condition that enables Oracle-specific R2DBC repository operations for Oracle datasources.
 */
@Internal
final class OracleR2dbcRepositoryOperationsCondition implements Condition {

    /**
     * Checks whether the current datasource is configured with the Oracle dialect.
     *
     * @param context The condition context
     * @return {@code true} when Oracle R2DBC operations should be enabled
     */
    @Override
    public boolean matches(ConditionContext context) {
        return R2dbcRepositoryOperationsConditions.isOracleDialect(context);
    }
}

/**
 * Condition that enables SQL Server-specific R2DBC repository operations for SQL Server datasources.
 */
@Internal
final class SqlServerR2dbcRepositoryOperationsCondition implements Condition {

    /**
     * Checks whether the current datasource is configured with the SQL Server dialect.
     *
     * @param context The condition context
     * @return {@code true} when SQL Server R2DBC operations should be enabled
     */
    @Override
    public boolean matches(ConditionContext context) {
        return R2dbcRepositoryOperationsConditions.isSqlServerDialect(context);
    }
}

/**
 * Shared condition utilities for selecting the R2DBC repository operations bean.
 */
@Internal
final class R2dbcRepositoryOperationsConditions {

    private static final String DATASOURCES = "r2dbc.datasources";
    private static final String DIALECT = "dialect";
    private static final String ORACLE_DIALECT = "ORACLE";
    private static final String SQL_SERVER_DIALECT = "SQL_SERVER";
    private static final String DEFAULT = "default";

    private R2dbcRepositoryOperationsConditions() {
    }

    /**
     * Checks whether the datasource associated with the current bean resolution uses the Oracle dialect.
     *
     * @param context The condition context
     * @return {@code true} when the datasource is configured with {@code r2dbc.datasources.<name>.dialect=ORACLE}
     */
    static boolean isOracleDialect(ConditionContext context) {
        return isDialect(context, ORACLE_DIALECT);
    }

    /**
     * Checks whether the datasource associated with the current bean resolution uses the SQL Server dialect.
     *
     * @param context The condition context
     * @return {@code true} when the datasource is configured with {@code r2dbc.datasources.<name>.dialect=SQL_SERVER}
     */
    static boolean isSqlServerDialect(ConditionContext context) {
        return isDialect(context, SQL_SERVER_DIALECT);
    }

    /**
     * Checks whether default R2DBC operations should remain available for the current condition evaluation.
     *
     * <p>When the condition is evaluated for a specific {@code @EachBean(ConnectionFactory)} instance,
     * the current datasource qualifier is available and only that datasource is considered. When
     * Micronaut evaluates the bean definition before a datasource qualifier is available, all
     * configured datasources are considered so mixed datasource applications can still create default
     * operations for non-special datasources while Oracle or SQL Server operations handle their own
     * datasources.</p>
     *
     * @param context The condition context
     * @return {@code true} when at least the current or one configured datasource should use default operations
     */
    static boolean isDefaultOperationsDialect(ConditionContext context) {
        Optional<String> dataSourceName = DataSourceConfigurationUtils.resolveDataSourceName(context);
        if (dataSourceName.isPresent()) {
            return !isDialect(context, dataSourceName.get(), ORACLE_DIALECT)
                && !isDialect(context, dataSourceName.get(), SQL_SERVER_DIALECT);
        }
        List<String> dataSourceNames = DataSourceConfigurationUtils.resolveConfiguredDataSourceNames(context, DATASOURCES, DataR2dbcConfiguration.class);
        if (dataSourceNames.isEmpty()) {
            return true;
        }
        return dataSourceNames.stream()
            .anyMatch(name -> !isDialect(context, name, ORACLE_DIALECT) && !isDialect(context, name, SQL_SERVER_DIALECT));
    }

    private static boolean isDialect(ConditionContext context, String expectedDialect) {
        Optional<String> dataSourceName = DataSourceConfigurationUtils.resolveDataSourceName(context);
        if (dataSourceName.isPresent()) {
            return isDialect(context, dataSourceName.get(), expectedDialect);
        }
        List<String> dataSourceNames = DataSourceConfigurationUtils.resolveConfiguredDataSourceNames(context, DATASOURCES, DataR2dbcConfiguration.class);
        if (dataSourceNames.isEmpty()) {
            return isDialect(context, DEFAULT, expectedDialect);
        }
        return dataSourceNames.stream().anyMatch(name -> isDialect(context, name, expectedDialect));
    }

    private static boolean isDialect(ConditionContext context, String dataSourceName, String expectedDialect) {
        String dialectProperty = DATASOURCES + '.' + dataSourceName + '.' + DIALECT;
        String dialect = context.getProperty(dialectProperty, String.class).orElse(null);
        return expectedDialect.equalsIgnoreCase(dialect);
    }
}
