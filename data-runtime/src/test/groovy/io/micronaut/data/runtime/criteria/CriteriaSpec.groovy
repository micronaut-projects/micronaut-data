package io.micronaut.data.runtime.criteria

import io.micronaut.context.ApplicationContext
import io.micronaut.data.event.EntityEventListener
import io.micronaut.data.model.jpa.criteria.*
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.model.runtime.RuntimePersistentProperty
import io.micronaut.data.tck.tests.AbstractCriteriaSpec
import jakarta.persistence.criteria.CriteriaDelete
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.CriteriaUpdate
import jakarta.persistence.criteria.Expression
import spock.lang.Unroll

class CriteriaSpec extends AbstractCriteriaSpec {

    PersistentEntityCriteriaBuilder criteriaBuilder

    PersistentEntityCriteriaQuery criteriaQuery

    PersistentEntityCriteriaDelete criteriaDelete

    PersistentEntityCriteriaUpdate criteriaUpdate

    void setup() {
        Map<Class, RuntimePersistentEntity> map = new HashMap<>();
        criteriaBuilder = new RuntimeCriteriaBuilder(new RuntimeEntityRegistry() {
            @Override
            EntityEventListener<Object> getEntityEventListener() {
                throw new IllegalStateException()
            }

            @Override
            Object autoPopulateRuntimeProperty(RuntimePersistentProperty<?> persistentProperty, Object previousValue) {
                throw new IllegalStateException()
            }

            @Override
             <T> RuntimePersistentEntity<T> getEntity(Class<T> type) {
                return map.computeIfAbsent(type, RuntimePersistentEntity::new)
            }

            @Override
             <T> RuntimePersistentEntity<T> newEntity(Class<T> type) {
                throw new IllegalStateException()
            }

            @Override
            ApplicationContext getApplicationContext() {
                throw new IllegalStateException()
            }
        })
        criteriaQuery = criteriaBuilder.createQuery()
        criteriaDelete = criteriaBuilder.createCriteriaDelete(Test)
        criteriaUpdate = criteriaBuilder.createCriteriaUpdate(Test)
    }

    @Override
    PersistentEntityRoot createRoot(CriteriaQuery query) {
        return query.from(Test)
    }

    @Override
    PersistentEntityRoot createRoot(CriteriaDelete query) {
        return query.from(Test)
    }

    @Override
    PersistentEntityRoot createRoot(CriteriaUpdate query) {
        return query.from(Test)
    }

    @Unroll
    void "test criteria predicate"(Specification specification) {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            def predicate = specification.toPredicate(entityRoot, criteriaQuery, criteriaBuilder)
            if (predicate) {
                criteriaQuery.where(predicate)
            }
            String whereSqlQuery = getWhereQueryPart(criteriaQuery)

        expect:
            whereSqlQuery == expectedWhereQuery

        where:
            specification << [
                    { root, query, cb ->
                        root.get("amount").in(100, 200)
                    } as Specification,
                    { root, query, cb ->
                        root.get("amount").in(100, 200).not()
                    } as Specification,
                    { root, query, cb ->
                        cb.in(root.get("amount")).value(100).value(200)
                    } as Specification,
                    { root, query, cb ->
                        cb.in(root.get("amount")).value(100).value(200).not()
                    } as Specification,
                    { root, query, cb ->
                        def parameter = cb.parameter(Integer)
                        root.get("amount").in([parameter] as Expression<?>[])
                    } as Specification,
                    { root, query, cb ->
                        def parameter = cb.parameter(Integer)
                        root.get("amount").in([parameter] as Expression<?>[]).not()
                    } as Specification,
                    { root, query, cb ->
                        cb.between(root.get("enabled"), true, false)
                    } as Specification,
                    { root, query, cb ->
                        def parameter = cb.parameter(Integer)
                        cb.between(root.get("amount"), parameter, parameter)
                    } as Specification,
                    { root, query, cb ->
                        query.where(root.get("enabled"))
                        null
                    } as Specification,
                    { root, query, cb ->
                        query.where(root.get("enabled"))
                        query.orderBy(cb.desc(root.get("amount")), cb.asc(root.get("budget")))
                        null
                    } as Specification,
                    { root, query, cb ->
                        def pred1 = cb.or(root.get("enabled"), root.get("enabled2"))
                        def pred2 = cb.or(pred1, cb.equal(root.get("amount"), 100))
                        def andPred = cb.and(cb.equal(root.get("budget"), 200), pred2)
                        andPred
                    } as Specification
            ]
            expectedWhereQuery << [
                    '(test_."amount" IN (?))',
                    '(test_."amount" NOT IN (?))',
                    '(test_."amount" IN (?))',
                    '(test_."amount" NOT IN (?))',
                    '(test_."amount" IN (?))',
                    '(test_."amount" NOT IN (?))',
                    '((test_."enabled" >= ? AND test_."enabled" <= ?))',
                    '((test_."amount" >= ? AND test_."amount" <= ?))',
                    '(test_."enabled" = TRUE)',
                    '(test_."enabled" = TRUE) ORDER BY test_."amount" DESC,test_."budget" ASC',
                    '(test_."budget" = ? AND ((test_."enabled" = TRUE OR test_."enabled2" = TRUE) OR test_."amount" = ?))'
            ]
    }


    @Unroll
    void "test delete"(DeleteSpecification specification) {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaDelete)
            def predicate = specification.toPredicate(entityRoot, criteriaDelete, criteriaBuilder)
            if (predicate) {
                criteriaDelete.where(predicate)
            }
            String sqlQuery = getSqlQuery(criteriaDelete)

        expect:
            sqlQuery == expectedQuery

        where:
            specification << [
                    { root, query, cb ->
                        cb.ge(root.get("amount"), 1000)
                    } as DeleteSpecification,
            ]
            expectedQuery << [
                    'DELETE  FROM "test"  WHERE ("amount" >= ?)',
            ]
    }

    @Unroll
    void "test update"(UpdateSpecification specification) {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaUpdate)
            def predicate = specification.toPredicate(entityRoot, criteriaUpdate, criteriaBuilder)
            if (predicate) {
                criteriaUpdate.where(predicate)
            }
            String sqlQuery = getSqlQuery(criteriaUpdate)

        expect:
            sqlQuery == expectedQuery

        where:
            specification << [
                    { root, query, cb ->
                        query.set("name", "ABC")
                        query.set(root.get("amount"), 123)
                        cb.ge(root.get("amount"), 1000)
                    } as UpdateSpecification,
                    { root, query, cb ->
                        query.set("name", cb.parameter(String))
                        query.set(root.get("amount"), cb.parameter(Integer))
                        cb.ge(root.get("amount"), 1000)
                    } as UpdateSpecification,
                    { root, query, cb ->
                        query.set("name", "test")
                        query.set(root.get("amount"), cb.parameter(Integer))
                        cb.ge(root.get("amount"), 1000)
                    } as UpdateSpecification,
            ]
            expectedQuery << [
                    'UPDATE "test" SET "name"=?,"amount"=? WHERE ("amount" >= ?)',
                    'UPDATE "test" SET "name"=?,"amount"=? WHERE ("amount" >= ?)',
                    'UPDATE "test" SET "name"=?,"amount"=? WHERE ("amount" >= ?)',
            ]
    }

    @Unroll
    void "test properties not #predicate predicate produces where query: #expectedWhereQuery"() {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            criteriaQuery.where(predicateProps(predicate, entityRoot, property1, property2).not())
            def whereSqlQuery = getWhereQueryPart(criteriaQuery)

        expect:
            whereSqlQuery == expectedWhereQuery

        where:
            property1 | property2  | predicate              | expectedWhereQuery
            "enabled" | "enabled2" | "equal"                | '(test_."enabled"!=test_."enabled2")'
            "enabled" | "enabled2" | "notEqual"             | '(test_."enabled"=test_."enabled2")'
            "enabled" | "enabled2" | "greaterThan"          | '(NOT(test_."enabled">test_."enabled2"))'
            "enabled" | "enabled2" | "greaterThanOrEqualTo" | '(NOT(test_."enabled">=test_."enabled2"))'
            "enabled" | "enabled2" | "lessThan"             | '(NOT(test_."enabled"<test_."enabled2"))'
            "enabled" | "enabled2" | "lessThanOrEqualTo"    | '(NOT(test_."enabled"<=test_."enabled2"))'
            "amount"  | "budget"   | "gt"                   | '(NOT(test_."amount">test_."budget"))'
            "amount"  | "budget"   | "ge"                   | '(NOT(test_."amount">=test_."budget"))'
            "amount"  | "budget"   | "lt"                   | '(NOT(test_."amount"<test_."budget"))'
            "amount"  | "budget"   | "le"                   | '(NOT(test_."amount"<=test_."budget"))'
    }

    @Unroll
    void "test property value #predicate predicate produces where query: #expectedWhereQuery"() {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            criteriaQuery.where(predicateValue(predicate, entityRoot, property1, value))
            def whereSqlQuery = getWhereQueryPart(criteriaQuery)

        expect:
            whereSqlQuery == expectedWhereQuery

        where:
            property1 | value                   | predicate              | expectedWhereQuery
            "enabled" | true                    | "equal"                | '(test_."enabled" = ?)'
            "enabled" | true                    | "notEqual"             | '(test_."enabled" != ?)'
            "enabled" | true                    | "greaterThan"          | '(test_."enabled" > ?)'
            "enabled" | true                    | "greaterThanOrEqualTo" | '(test_."enabled" >= ?)'
            "enabled" | true                    | "lessThan"             | '(test_."enabled" < ?)'
            "enabled" | true                    | "lessThanOrEqualTo"    | '(test_."enabled" <= ?)'
            "amount"  | BigDecimal.valueOf(100) | "gt"                   | '(test_."amount" > ?)'
            "amount"  | BigDecimal.valueOf(100) | "ge"                   | '(test_."amount" >= ?)'
            "amount"  | BigDecimal.valueOf(100) | "lt"                   | '(test_."amount" < ?)'
            "amount"  | BigDecimal.valueOf(100) | "le"                   | '(test_."amount" <= ?)'
    }

    @Unroll
    void "test property value not #predicate predicate produces where query: #expectedWhereQuery"() {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            criteriaQuery.where(predicateValue(predicate, entityRoot, property1, value).not())
            def whereSqlQuery = getWhereQueryPart(criteriaQuery)

        expect:
            whereSqlQuery == expectedWhereQuery

        where:
            property1 | value                   | predicate              | expectedWhereQuery
            "enabled" | true                    | "equal"                | '(test_."enabled" != ?)'
            "enabled" | true                    | "notEqual"             | '(test_."enabled" = ?)'
            "enabled" | true                    | "greaterThan"          | '(NOT(test_."enabled" > ?))'
            "enabled" | true                    | "greaterThanOrEqualTo" | '(NOT(test_."enabled" >= ?))'
            "enabled" | true                    | "lessThan"             | '(NOT(test_."enabled" < ?))'
            "enabled" | true                    | "lessThanOrEqualTo"    | '(NOT(test_."enabled" <= ?))'
            "amount"  | BigDecimal.valueOf(100) | "gt"                   | '(NOT(test_."amount" > ?))'
            "amount"  | BigDecimal.valueOf(100) | "ge"                   | '(NOT(test_."amount" >= ?))'
            "amount"  | BigDecimal.valueOf(100) | "lt"                   | '(NOT(test_."amount" < ?))'
            "amount"  | BigDecimal.valueOf(100) | "le"                   | '(NOT(test_."amount" <= ?))'
    }

}
