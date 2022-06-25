package io.micronaut.data.runtime.criteria.ext

import io.micronaut.data.model.jpa.criteria.impl.QueryResultPersistentEntityCriteriaQuery
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.runtime.criteria.RuntimeCriteriaBuilder
import io.micronaut.data.runtime.criteria.get
import io.micronaut.data.runtime.criteria.joinMany
import io.micronaut.data.runtime.criteria.query
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

        Assertions.assertEquals(q, """SELECT MAX(test_entity_."birth") FROM "test_entity" test_entity_ WHERE (test_entity_."name" = 'XYZ' AND test_entity_."enabled" = TRUE  AND (test_entity_."name" = 'AAA' OR test_entity_."name" = 'BBB'))""")
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
                    root[TestEntity::enabled].eq(true)
                    root[TestEntity::enabled].ne(false)
                    root[TestEntity::enabled].equal(true)
                    root[TestEntity::enabled].notEqual(false)
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

        Assertions.assertEquals(q, """SELECT MIN(test_entity_."name") FROM "test_entity" test_entity_ WHERE ((test_entity_."enabled" = TRUE  OR test_entity_."enabled" = FALSE  OR test_entity_."enabled" IS NULL  OR test_entity_."enabled" IS NOT NULL ) AND  NOT(test_entity_."enabled" = TRUE) AND  NOT(test_entity_."enabled" != FALSE) AND  NOT(test_entity_."enabled" = TRUE) AND  NOT(test_entity_."enabled" != FALSE) AND (test_entity_."name" < 'A' OR test_entity_."name" < 'B') AND test_entity_."name" > 'C' AND test_entity_."name" >= 'D' AND ((test_entity_."age" >= 10 AND test_entity_."age" <= 100) OR test_entity_."age" > 20 OR test_entity_."age" >= 30 OR test_entity_."age" < 40 OR test_entity_."age" <= 50))""")
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

        Assertions.assertEquals(q, """SELECT MIN(test_entity_."name") FROM "test_entity" test_entity_ WHERE ( NOT(test_entity_."enabled" = NULL) AND  NOT(test_entity_."enabled" != NULL) AND  NOT(test_entity_."enabled" = NULL) AND  NOT(test_entity_."enabled" != NULL) AND (test_entity_."enabled" = NULL OR test_entity_."enabled" != NULL OR test_entity_."enabled" = NULL OR test_entity_."enabled" != NULL) AND test_entity_."description" = NULL AND test_entity_."description" != NULL AND test_entity_."description" = NULL AND test_entity_."description" != NULL)""")
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

        Assertions.assertEquals(q, """SELECT MIN(test_entity_."name") FROM "test_entity" test_entity_ INNER JOIN "other_entity" test_entity_others_ ON test_entity_."id"=test_entity_others_."test_id" WHERE ( NOT(test_entity_."enabled"=test_entity_others_."enabled") AND  NOT(test_entity_."enabled"!=test_entity_others_."enabled") AND  NOT(test_entity_."enabled"=test_entity_others_."enabled") AND  NOT(test_entity_."enabled"!=test_entity_others_."enabled") AND (test_entity_."name"<test_entity_others_."name" OR test_entity_."name"<test_entity_others_."name") AND test_entity_."name">test_entity_others_."name" AND test_entity_."name">=test_entity_others_."name" AND (test_entity_."age">test_entity_others_."age" OR test_entity_."age">=test_entity_others_."age" OR test_entity_."age"<test_entity_others_."age" OR test_entity_."age"<=test_entity_others_."age"))""")
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

        Assertions.assertEquals(q, """SELECT AVG(other_entity_."age"),MAX(other_entity_."age"),MIN(other_entity_."age"),MAX(other_entity_."name"),MIN(other_entity_."name") FROM "other_entity" other_entity_ WHERE (other_entity_."name" = 'Xyz')""")
    }

}
