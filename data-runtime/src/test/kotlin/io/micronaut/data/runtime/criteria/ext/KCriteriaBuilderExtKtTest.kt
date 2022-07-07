package io.micronaut.data.runtime.criteria.ext

import io.micronaut.data.model.jpa.criteria.impl.QueryResultPersistentEntityCriteriaQuery
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.runtime.criteria.*
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.LocalDate

@MicronautTest
class KCriteriaBuilderExtKtTest(var runtimeCriteriaBuilder: RuntimeCriteriaBuilder) {

    @Test
    fun testBasic() {
        val query = query<TestEntity, LocalDate> {
            select(greatest(TestEntity::birth))
            where {
                (root[TestEntity::name] eq "XYZ")
                and {
                    root[TestEntity::enabled].equalsTrue()
                    or {
                        (root[TestEntity::name] eq "AAA")
                        (root[TestEntity::name] eq "BBB")
                    }
                }
            }
        }
        val criteriaQuery = query.build(runtimeCriteriaBuilder) as QueryResultPersistentEntityCriteriaQuery
        val q = criteriaQuery.buildQuery(SqlQueryBuilder()).query

        Assertions.assertEquals("""SELECT MAX(test_entity_."birth") FROM "test_entity" test_entity_ WHERE (test_entity_."name" = ? AND test_entity_."enabled" = TRUE  AND (test_entity_."name" = ? OR test_entity_."name" = ?))""", q)
    }

    @Test
    fun testPredicates() {
        val query = query<TestEntity, String> {
            select(least(TestEntity::name))
            where {
                or {
                    root[TestEntity::enabled].equalsTrue()
                    root[TestEntity::enabled].equalsFalse()
                    root[TestEntity::enabled].equalsNull()
                    root[TestEntity::enabled].notEqualsNull()
                }
                not {
                    root[TestEntity::enabled] eq true
                    root[TestEntity::enabled] ne false
                    root[TestEntity::enabled] equal true
                    root[TestEntity::enabled] notEqual false
                }
                or {
                    (root[TestEntity::name] lessThan "A")
                    (root[TestEntity::name] lessThanOrEqualTo "B")
                }
                and {
                    (root[TestEntity::name] greaterThan "C")
                    (root[TestEntity::name] greaterThanOrEqualTo "D")
                }
                or {
                    root[TestEntity::age].between(10, 100)
                    (root[TestEntity::age] gt 20)
                    (root[TestEntity::age] ge 30)
                    (root[TestEntity::age] lt 40)
                    (root[TestEntity::age] le 50)
                }
            }
        }
        val criteriaQuery = query.build(runtimeCriteriaBuilder) as QueryResultPersistentEntityCriteriaQuery
        val q = criteriaQuery.buildQuery(SqlQueryBuilder()).query

        Assertions.assertEquals( """SELECT MIN(test_entity_."name") FROM "test_entity" test_entity_ WHERE ((test_entity_."enabled" = TRUE  OR test_entity_."enabled" = FALSE  OR test_entity_."enabled" IS NULL  OR test_entity_."enabled" IS NOT NULL ) AND  NOT(test_entity_."enabled" = ?) AND  NOT(test_entity_."enabled" != ?) AND  NOT(test_entity_."enabled" = ?) AND  NOT(test_entity_."enabled" != ?) AND (test_entity_."name" < ? OR test_entity_."name" < ?) AND test_entity_."name" > ? AND test_entity_."name" >= ? AND ((test_entity_."age" >= ? AND test_entity_."age" <= ?) OR test_entity_."age" > ? OR test_entity_."age" >= ? OR test_entity_."age" < ? OR test_entity_."age" <= ?))""", q)
    }

    @Test
    fun testPredicatesNull() {
        val query = query<TestEntity, String> {
            select(least(TestEntity::name))
            where {
                val bool: Boolean? = null
                val str: String? = null
                not {
                    root[TestEntity::enabled].eq(bool)
                    root[TestEntity::enabled].ne(bool)
                    root[TestEntity::enabled].equal(bool)
                    root[TestEntity::enabled].notEqual(bool)
                }
                or {
                    root[TestEntity::enabled].eq(str)
                    root[TestEntity::enabled].ne(str)
                    root[TestEntity::enabled].equal(str)
                    root[TestEntity::enabled].notEqual(str)
                }
                and {
                    root[TestEntity::description].eq(str)
                    root[TestEntity::description].ne(str)
                    root[TestEntity::description].equal(str)
                    root[TestEntity::description].notEqual(str)
                }
            }
        }
        val criteriaQuery = query.build(runtimeCriteriaBuilder) as QueryResultPersistentEntityCriteriaQuery
        val q = criteriaQuery.buildQuery(SqlQueryBuilder()).query

        Assertions.assertEquals("""SELECT MIN(test_entity_."name") FROM "test_entity" test_entity_ WHERE ( NOT(test_entity_."enabled" = ?) AND  NOT(test_entity_."enabled" != ?) AND  NOT(test_entity_."enabled" = ?) AND  NOT(test_entity_."enabled" != ?) AND (test_entity_."enabled" = ? OR test_entity_."enabled" != ? OR test_entity_."enabled" = ? OR test_entity_."enabled" != ?) AND test_entity_."description" = ? AND test_entity_."description" != ? AND test_entity_."description" = ? AND test_entity_."description" != ?)""", q)
    }

    @Test
    fun testJoin() {
        val query = query<TestEntity, String> {
            select(least(TestEntity::name))
            where {
                val others = root.joinMany(TestEntity::others)
                not {
                    root[TestEntity::enabled].eq(others[OtherEntity::enabled])
                    root[TestEntity::enabled].ne(others[OtherEntity::enabled])
                    root[TestEntity::enabled].equal(others[OtherEntity::enabled])
                    root[TestEntity::enabled].notEqual(others[OtherEntity::enabled])
                }
                or {
                    (root[TestEntity::name] lessThan others[OtherEntity::name])
                    (root[TestEntity::name] lessThanOrEqualTo others[OtherEntity::name])
                }
                and {
                    (root[TestEntity::name] greaterThan others[OtherEntity::name])
                    (root[TestEntity::name] greaterThanOrEqualTo others[OtherEntity::name])
                }
                or {
                    (root[TestEntity::age] gt others[OtherEntity::age])
                    (root[TestEntity::age] ge others[OtherEntity::age])
                    (root[TestEntity::age] lt others[OtherEntity::age])
                    (root[TestEntity::age] le others[OtherEntity::age])
                }
            }
        }
        val criteriaQuery = query.build(runtimeCriteriaBuilder) as QueryResultPersistentEntityCriteriaQuery
        val q = criteriaQuery.buildQuery(SqlQueryBuilder()).query

        Assertions.assertEquals("""SELECT MIN(test_entity_."name") FROM "test_entity" test_entity_ INNER JOIN "other_entity" test_entity_others_ ON test_entity_."id"=test_entity_others_."test_id" WHERE ( NOT(test_entity_."enabled"=test_entity_others_."enabled") AND  NOT(test_entity_."enabled"!=test_entity_others_."enabled") AND  NOT(test_entity_."enabled"=test_entity_others_."enabled") AND  NOT(test_entity_."enabled"!=test_entity_others_."enabled") AND (test_entity_."name"<test_entity_others_."name" OR test_entity_."name"<test_entity_others_."name") AND test_entity_."name">test_entity_others_."name" AND test_entity_."name">=test_entity_others_."name" AND (test_entity_."age">test_entity_others_."age" OR test_entity_."age">=test_entity_others_."age" OR test_entity_."age"<test_entity_others_."age" OR test_entity_."age"<=test_entity_others_."age"))""", q)
    }

    @Test
    fun testMultiselect() {
        val query = query<OtherEntity, Any> {
            multiselect(avg(OtherEntity::age), max(OtherEntity::age), min(OtherEntity::age), greatest(OtherEntity::name), least(OtherEntity::name))
            where {
                root[OtherEntity::name] eq "Xyz"
            }
        }
        val criteriaQuery = query.build(runtimeCriteriaBuilder) as QueryResultPersistentEntityCriteriaQuery
        val q = criteriaQuery.buildQuery(SqlQueryBuilder()).query

        Assertions.assertEquals("""SELECT AVG(other_entity_."age"),MAX(other_entity_."age"),MIN(other_entity_."age"),MAX(other_entity_."name"),MIN(other_entity_."name") FROM "other_entity" other_entity_ WHERE (other_entity_."name" = ?)""", q)
    }

    @Test
    fun testUpdate() {
        val updateQuery = update<OtherEntity> {
            set(OtherEntity::name, "xx")
            where {
                root[OtherEntity::name] eq "Xyz"
            }
        }
        val criteriaQuery = updateQuery.build(runtimeCriteriaBuilder) as QueryResultPersistentEntityCriteriaQuery
        val q = criteriaQuery.buildQuery(SqlQueryBuilder()).query

        Assertions.assertEquals("""UPDATE "other_entity" SET "name"=? WHERE ("name" = ?)""", q)
    }

}
