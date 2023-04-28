package io.micronaut.data.spring.jdbc;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.transaction.support.AbstractDataSourceTransactionManagerCondition;

@Introspected
final class SpringJdbcTransactionManagerCondition extends AbstractDataSourceTransactionManagerCondition {

    @Override
    protected String getTransactionManagerName() {
        return "springJdbc";
    }

}
