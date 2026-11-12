from micronaut.data.model import PersistentEntity
from micronaut.data.model.query.builder.sql import Dialect, SqlQueryBuilder
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from example.Plan import Plan
from example.Subscriber import Subscriber
from example.Subscription import Subscription


@MicronautTest(startApplication=False)
class ShardedTablesSpec:

    @Test
    def shardedTableFamily(self):
        builder = SqlQueryBuilder(Dialect.ORACLE)
        root = builder.buildCreateTableStatements(PersistentEntity.of(Subscriber))[0]
        assert root.startswith('CREATE SHARDED TABLE "SUBSCRIBER" ('), root
        assert root.endswith(') PARTITION BY CONSISTENT HASH ("SUBSCRIBER_ID") PARTITIONS AUTO TABLESPACE SET TS_SUBSCRIBERS'), root

        child = builder.buildCreateTableStatements(PersistentEntity.of(Subscription))[0]
        assert child.startswith('CREATE SHARDED TABLE "SUBSCRIPTION" ('), child
        assert 'PRIMARY KEY("SUBSCRIBER_ID","SUBSCRIPTION_NO")' in child, child
        assert child.endswith(') PARENT "SUBSCRIBER" PARTITION BY CONSISTENT HASH ("SUBSCRIBER_ID") PARTITIONS AUTO TABLESPACE SET TS_SUBSCRIBERS'), child

    @Test
    def duplicatedTable(self):
        sql = SqlQueryBuilder(Dialect.ORACLE).buildCreateTableStatements(PersistentEntity.of(Plan))[0]
        assert sql.startswith('CREATE DUPLICATED TABLE "PLAN" ('), sql
        assert sql.endswith(') TABLESPACE PLANS_TSP'), sql
