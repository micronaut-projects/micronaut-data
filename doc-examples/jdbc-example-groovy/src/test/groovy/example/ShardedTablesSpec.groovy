package example

import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import spock.lang.Specification

class ShardedTablesSpec extends Specification {

    SqlQueryBuilder builder = new SqlQueryBuilder(Dialect.ORACLE)

    void 'test sharded table family'() {
        when:
        String root = builder.buildCreateTableStatements(PersistentEntity.of(Subscriber))[0]
        String child = builder.buildCreateTableStatements(PersistentEntity.of(Subscription))[0]

        then:
        root.startsWith('CREATE SHARDED TABLE "SUBSCRIBER" (')
        root.endsWith(') PARTITION BY CONSISTENT HASH ("SUBSCRIBER_ID") PARTITIONS AUTO TABLESPACE SET TS_SUBSCRIBERS')
        child.startsWith('CREATE SHARDED TABLE "SUBSCRIPTION" (')
        child.contains('PRIMARY KEY("SUBSCRIBER_ID","SUBSCRIPTION_NO")')
        child.endsWith(') PARENT "SUBSCRIBER" PARTITION BY CONSISTENT HASH ("SUBSCRIBER_ID") PARTITIONS AUTO TABLESPACE SET TS_SUBSCRIBERS')
    }

    void 'test duplicated table'() {
        when:
        String sql = builder.buildCreateTableStatements(PersistentEntity.of(Plan))[0]

        then:
        sql.startsWith('CREATE DUPLICATED TABLE "PLAN" (')
        sql.endsWith(') TABLESPACE PLANS_TSP')
    }
}
