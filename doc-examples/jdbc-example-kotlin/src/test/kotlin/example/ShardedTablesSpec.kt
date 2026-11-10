package example

import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShardedTablesSpec {

    private val builder = SqlQueryBuilder(Dialect.ORACLE)

    @Test
    fun testShardedTableFamily() {
        val root = builder.buildCreateTableStatements(PersistentEntity.of(Subscriber::class.java))[0]
        assertTrue(root.startsWith("CREATE SHARDED TABLE \"SUBSCRIBER\" ("), root)
        assertTrue(root.endsWith(") PARTITION BY CONSISTENT HASH (\"SUBSCRIBER_ID\") PARTITIONS AUTO TABLESPACE SET TS_SUBSCRIBERS"), root)

        val child = builder.buildCreateTableStatements(PersistentEntity.of(Subscription::class.java))[0]
        assertTrue(child.startsWith("CREATE SHARDED TABLE \"SUBSCRIPTION\" ("), child)
        assertTrue(child.contains("PRIMARY KEY(\"SUBSCRIBER_ID\",\"SUBSCRIPTION_NO\")"), child)
        assertTrue(child.endsWith(") PARENT \"SUBSCRIBER\" PARTITION BY CONSISTENT HASH (\"SUBSCRIBER_ID\") PARTITIONS AUTO TABLESPACE SET TS_SUBSCRIBERS"), child)
    }

    @Test
    fun testDuplicatedTable() {
        val sql = builder.buildCreateTableStatements(PersistentEntity.of(Plan::class.java))[0]
        assertTrue(sql.startsWith("CREATE DUPLICATED TABLE \"PLAN\" ("), sql)
        assertTrue(sql.endsWith(") TABLESPACE PLANS_TSP"), sql)
    }
}
