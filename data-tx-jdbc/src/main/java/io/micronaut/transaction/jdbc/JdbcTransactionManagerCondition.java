package io.micronaut.transaction.jdbc;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.transaction.support.AbstractDataSourceTransactionManagerCondition;

@Introspected
final class JdbcTransactionManagerCondition extends AbstractDataSourceTransactionManagerCondition {

    @Override
    protected String getTransactionManagerName() {
        return "jdbc";
    }

}
