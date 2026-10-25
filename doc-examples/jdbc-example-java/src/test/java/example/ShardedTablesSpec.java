package example;

import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ShardedTablesSpec {

    private final SqlQueryBuilder builder = new SqlQueryBuilder(Dialect.ORACLE);

    @Test
    void testShardedTableFamily() {
        String root = builder.buildCreateTableStatements(PersistentEntity.of(Subscriber.class))[0];
        assertTrue(root.startsWith("CREATE SHARDED TABLE \"SUBSCRIBER\" ("), root);
        assertTrue(root.endsWith(") PARTITION BY CONSISTENT HASH (\"SUBSCRIBER_ID\") PARTITIONS AUTO TABLESPACE SET TS_SUBSCRIBERS"), root);

        String child = builder.buildCreateTableStatements(PersistentEntity.of(Subscription.class))[0];
        assertTrue(child.startsWith("CREATE SHARDED TABLE \"SUBSCRIPTION\" ("), child);
        assertTrue(child.contains("PRIMARY KEY(\"SUBSCRIBER_ID\",\"SUBSCRIPTION_NO\")"), child);
        assertTrue(child.endsWith(") PARENT \"SUBSCRIBER\" PARTITION BY CONSISTENT HASH (\"SUBSCRIBER_ID\") PARTITIONS AUTO TABLESPACE SET TS_SUBSCRIBERS"), child);
    }

    @Test
    void testDuplicatedTable() {
        String sql = builder.buildCreateTableStatements(PersistentEntity.of(Plan.class))[0];
        assertTrue(sql.startsWith("CREATE DUPLICATED TABLE \"PLAN\" ("), sql);
        assertTrue(sql.endsWith(") TABLESPACE PLANS_TSP"), sql);
    }
}
