package io.micronaut.data.runtime.criteria

import io.micronaut.context.ApplicationContext
import io.micronaut.data.event.EntityEventListener
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaBuilder
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaDelete
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQuery
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaUpdate
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.model.runtime.RuntimePersistentProperty
import io.micronaut.data.tck.tests.AbstractCriteriaSpec
import jakarta.persistence.criteria.CriteriaDelete
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.CriteriaUpdate
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.JoinType
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Selection
import spock.lang.Unroll

class StaticCriteriaSpec extends AbstractCriteriaSpec {

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

    @Unroll
    void "test criteria predicate2"(Specification specification) {
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
                        root.get(Test_.amount).in(100, 200)
                    } as Specification,
                    { root, query, cb ->
                        root.get(Test_.amount).in(100, 200).not()
                    } as Specification,
                    { root, query, cb ->
                        cb.in(root.get(Test_.amount)).value(100).value(200)
                    } as Specification,
                    { root, query, cb ->
                        cb.in(root.get(Test_.amount)).value(100).value(200).not()
                    } as Specification,
                    { root, query, cb ->
                        def parameter = cb.parameter(Integer)
                        root.get(Test_.amount).in([parameter] as Expression<?>[])
                    } as Specification,
                    { root, query, cb ->
                        def parameter = cb.parameter(Integer)
                        root.get(Test_.amount).in([parameter] as Expression<?>[]).not()
                    } as Specification,
                    { root, query, cb ->
                        cb.between(root.get(Test_.enabled), true, false)
                    } as Specification,
                    { root, query, cb ->
                        def parameter = cb.parameter(Integer)
                        cb.between(root.get(Test_.amount), parameter, parameter)
                    } as Specification,
                    { root, query, cb ->
                        query.where(root.get(Test_.enabled))
                        null
                    } as Specification,
                    { root, query, cb ->
                        query.where(root.get(Test_.enabled))
                        query.orderBy(cb.desc(root.get(Test_.amount)), cb.asc(root.get(Test_.budget)))
                        null
                    } as Specification,
                    { root, query, cb ->
                        def pred1 = cb.or(root.get(Test_.enabled), root.get(Test_.enabled2))
                        def pred2 = cb.or(pred1, cb.equal(root.get(Test_.amount), 100))
                        def andPred = cb.and(cb.equal(root.get(Test_.budget), 200), pred2)
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
    void "test joins2"(Specification specification) {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            def predicate = specification.toPredicate(entityRoot, criteriaQuery, criteriaBuilder)
            if (predicate) {
                criteriaQuery.where(predicate)
            }
            String sqlQuery = getSqlQuery(criteriaQuery)

        expect:
            sqlQuery == expectedQuery

        where:
            specification << [
                    { root, query, cb ->
                        def othersJoin = root.join(Test_.others)
                        cb.and(cb.equal(root.get(Test_.amount), othersJoin.get(Test_.amount)))
                    } as Specification,

                    { root, query, cb ->
                        def othersJoin = root.join(Test_.others)
                        def simpleJoin = othersJoin.join(OtherEntity_.simple)
                        cb.and(
                                cb.equal(root.get(Test_.amount), othersJoin.get(OtherEntity_.amount)),
                                cb.equal(root.get(Test_.amount), simpleJoin.get(SimpleEntity_.amount)),
                        )
                    } as Specification,
                    { root, query, cb ->
                        root.join(Test_.others, JoinType.INNER)
                        return null
                    } as Specification,
                    { root, query, cb ->
                        root.joinList("others", JoinType.INNER)
                        return null
                    } as Specification,
                    { root, query, cb ->
                        root.joinList("others", JoinType.INNER)
                        return null
                    } as Specification,
                    { root, query, cb ->
                        root.join("others", JoinType.INNER)
                        root.joinList("others", JoinType.INNER)
                        return null
                    } as Specification,
            ]
            expectedQuery << [
                    'SELECT test_."id",test_."name",test_."enabled2",test_."enabled",test_."age",test_."amount",test_."budget" ' +
                            'FROM "test" test_ ' +
                            'INNER JOIN "other_entity" test_others_ ON test_."id"=test_others_."test_id"' +
                            ' WHERE (test_."amount"=test_others_."amount")',

                    'SELECT test_."id",test_."name",test_."enabled2",test_."enabled",test_."age",test_."amount",test_."budget" ' +
                            'FROM "test" test_ INNER JOIN "other_entity" test_others_ ON test_."id"=test_others_."test_id" ' +
                            'INNER JOIN "simple_entity" test_others_simple_ ON test_others_."simple_id"=test_others_simple_."id" ' +
                            'WHERE (test_."amount"=test_others_."amount" AND test_."amount"=test_others_simple_."amount")',
                    'SELECT test_."id",test_."name",test_."enabled2",test_."enabled",test_."age",test_."amount",test_."budget" FROM "test" test_ INNER JOIN "other_entity" test_others_ ON test_."id"=test_others_."test_id"',
                    'SELECT test_."id",test_."name",test_."enabled2",test_."enabled",test_."age",test_."amount",test_."budget" FROM "test" test_ INNER JOIN "other_entity" test_others_ ON test_."id"=test_others_."test_id"',
                    'SELECT test_."id",test_."name",test_."enabled2",test_."enabled",test_."age",test_."amount",test_."budget" FROM "test" test_ INNER JOIN "other_entity" test_others_ ON test_."id"=test_others_."test_id"',
                    'SELECT test_."id",test_."name",test_."enabled2",test_."enabled",test_."age",test_."amount",test_."budget" FROM "test" test_ INNER JOIN "other_entity" test_others_ ON test_."id"=test_others_."test_id"'
            ]
    }

    @Unroll
    void "test delete2"(DeleteSpecification specification) {
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
                        cb.ge(root.get(Test_.amount), 1000)
                    } as DeleteSpecification,
            ]
            expectedQuery << [
                    'DELETE  FROM "test"  WHERE ("amount" >= ?)',
            ]
    }

    @Unroll
    void "test update2"(UpdateSpecification specification) {
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
                        query.set(root.get(Test_.amount), 123)
                        cb.ge(root.get(Test_.amount), 1000)
                    } as UpdateSpecification,
                    { root, query, cb ->
                        query.set("name", cb.parameter(String))
                        query.set(root.get(Test_.amount), cb.parameter(Integer))
                        cb.ge(root.get(Test_.amount), 1000)
                    } as UpdateSpecification,
                    { root, query, cb ->
                        query.set("name", "test")
                        query.set(root.get(Test_.amount), cb.parameter(Integer))
                        cb.ge(root.get(Test_.amount), 1000)
                    } as UpdateSpecification,
            ]
            expectedQuery << [
                    'UPDATE "test" SET "name"=?,"amount"=? WHERE ("amount" >= ?)',
                    'UPDATE "test" SET "name"=?,"amount"=? WHERE ("amount" >= ?)',
                    'UPDATE "test" SET "name"=?,"amount"=? WHERE ("amount" >= ?)',
            ]
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

    protected Selection project(String projection, PersistentEntityRoot root, String property) {
        def prop = Test_."$property"
        criteriaBuilder."$projection"(root.get(prop))
    }

    protected Predicate predicateValue(String predicate, PersistentEntityRoot root, String property, Object value) {
        def prop = Test_."$property"
        criteriaBuilder."$predicate"(root.get(prop), value)
    }

    protected Predicate predicateProp(String predicate, PersistentEntityRoot root, String property1) {
        def prop1 = Test_."$property1"
        criteriaBuilder."$predicate"(root.get(prop1))
    }

    protected Predicate predicateProps(String predicate, PersistentEntityRoot root, String property1, String property2) {
        def prop1 = Test_."$property1"
        def prop2 = Test_."$property2"
        criteriaBuilder."$predicate"(root.get(prop1), root.get(prop2))
    }

}
