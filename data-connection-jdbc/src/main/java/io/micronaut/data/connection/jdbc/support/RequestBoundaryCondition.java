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
package io.micronaut.data.connection.jdbc.support;

import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.Qualifier;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.Named;
import io.micronaut.data.connection.jdbc.DataSourceConstants;
import io.micronaut.inject.BeanDefinition;

/**
 * Matches when {@code datasources.<name>.request-boundaries} is {@code true} for the data source.
 *
 * @since 5.3.0
 */
@Internal
final class RequestBoundaryCondition implements Condition {

    static final String REQUEST_BOUNDARIES = "request-boundaries";

    @Override
    public boolean matches(ConditionContext context) {
        BeanResolutionContext beanResolutionContext = context.getBeanResolutionContext();
        String dataSourceName = "default";
        if (beanResolutionContext != null) {
            Qualifier<?> currentQualifier = beanResolutionContext.getCurrentQualifier();
            if (currentQualifier == null && context.getComponent() instanceof BeanDefinition<?> definition) {
                currentQualifier = definition.getDeclaredQualifier();
            }
            if (currentQualifier instanceof Named named) {
                dataSourceName = named.getName();
            }
        }
        String property = DataSourceConstants.DATASOURCES + '.' + dataSourceName + '.' + REQUEST_BOUNDARIES;
        return context.getProperty(property, Boolean.class, false);
    }
}
