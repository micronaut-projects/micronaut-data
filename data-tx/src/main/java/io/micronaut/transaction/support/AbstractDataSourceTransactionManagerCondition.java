package io.micronaut.transaction.support;

import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.Qualifier;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.Named;

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
        return context.getProperty("datasources." + dataSourceName + ".transactionManager", String.class)
            .map(name -> name.equals(getTransactionManagerName()))
            .orElse(true);
    }
}
