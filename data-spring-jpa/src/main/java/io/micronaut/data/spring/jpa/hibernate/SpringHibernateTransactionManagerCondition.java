package io.micronaut.data.spring.jpa.hibernate;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.transaction.support.AbstractDataSourceTransactionManagerCondition;

@Introspected
final class SpringHibernateTransactionManagerCondition extends AbstractDataSourceTransactionManagerCondition {

    @Override
    protected String getTransactionManagerName() {
        return "springHibernate";
    }

}
