package io.micronaut.transaction.hibernate;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.transaction.support.AbstractDataSourceTransactionManagerCondition;

@Introspected
final class HibernateTransactionManagerCondition extends AbstractDataSourceTransactionManagerCondition {

    @Override
    protected String getTransactionManagerName() {
        return "hibernate";
    }

}
