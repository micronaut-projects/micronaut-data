package io.micronaut.data.nitrite.mongoport

import groovy.transform.CompileStatic
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.NonNull
import io.micronaut.data.nitrite.model.query.builder.NitriteQueryBuilder
import io.micronaut.data.nitrite.mongoport.entities.NitriteTestEntity
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaBuilder
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaDelete
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQuery
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaUpdate
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.model.runtime.RuntimePersistentProperty
import io.micronaut.data.event.EntityEventListener
import io.micronaut.data.runtime.criteria.RuntimeCriteriaBuilder
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaDelete
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.CriteriaUpdate
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import spock.lang.Specification
import spock.lang.Unroll

class NitriteCriteriaSpec extends Specification {

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
        criteriaDelete = criteriaBuilder.createCriteriaDelete(NitriteTestEntity)
        criteriaUpdate = criteriaBuilder.createCriteriaUpdate(NitriteTestEntity)
    }

    PersistentEntityRoot createRoot(CriteriaQuery query) {
        return query.from(NitriteTestEntity)
    }

    PersistentEntityRoot createRoot(CriteriaDelete query) {
        return query.from(NitriteTestEntity)
    }

    PersistentEntityRoot createRoot(CriteriaUpdate query) {
        return query.from(NitriteTestEntity)
    }

    @Unroll
    void "test criteria predicate"(QuerySpecification specification) {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            def predicate = specification.toPredicate(entityRoot, criteriaQuery, criteriaBuilder)
            if (predicate) {
                criteriaQuery.where(predicate)
            }
            String predicateQuery = getQuery(criteriaQuery)

        expect:
            predicateQuery == expectedWhereQuery

        where:
            specification << [
                    { root, query, cb ->
                        cb.between(root.get("enabled"), true, false)
                    } as QuerySpecification,
                    { root, query, cb ->
                        def parameter = cb.parameter(Integer)
                        cb.between(root.get("amount"), parameter, parameter)
                    } as QuerySpecification,
                    { root, query, cb ->
                        query.where(root.get("enabled"))
                        null
                    } as QuerySpecification,
                    { root, query, cb ->
                        cb.isTrue(root.get("enabled"))
                    } as QuerySpecification,
                    { root, query, cb ->
                        cb.and(cb.isTrue(root.get("enabled")), cb.isTrue(root.get("enabled")))
                    } as QuerySpecification,
                    { root, query, cb ->
                        root.get("name").in("A", "B", "C")
                    } as QuerySpecification,
                    { root, query, cb ->
                        cb.in(root.get("name")).value("A").value("B").value("C")
                    } as QuerySpecification,
                    { root, query, cb ->
                        root.get("name").in("A", "B", "C").not()
                    } as QuerySpecification,
            ]
            expectedWhereQuery << [
                    '{enabled:{$gte:{$mn_qp:0},$lte:{$mn_qp:1}}}',
                    '{amount:{$gte:{$mn_qp:0},$lte:{$mn_qp:1}}}',
                    '{enabled:{$eq:true}}',
                    '{enabled:{$eq:true}}',
                    '{$and:[{enabled:{$eq:true}},{enabled:{$eq:true}}]}',
                    '''{name:{$in:[{$mn_qp:0},{$mn_qp:1},{$mn_qp:2}]}}''',
                    '''{name:{$in:[{$mn_qp:0},{$mn_qp:1},{$mn_qp:2}]}}''',
                    '''{name:{$nin:[{$mn_qp:0},{$mn_qp:1},{$mn_qp:2}]}}''',
            ]
    }

    @Unroll
    void "test projection #projection"() {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            criteriaQuery.select(criteriaBuilder."$projection"(entityRoot.get(property)))
            def predicateQuery = getQuery(criteriaQuery)

        expect:
            predicateQuery == expectedWhereQuery

        where:
            property | projection | expectedWhereQuery
            "age"    | "max"      | '''[{$group:{age:{$max:'$age'},_id:null}}]'''
            "age"    | "min"      | '''[{$group:{age:{$min:'$age'},_id:null}}]'''
            "age"    | "avg"      | '''[{$group:{age:{$avg:'$age'},_id:null}}]'''
            "age"    | "sum"      | '''[{$group:{age:{$sum:'$age'},_id:null}}]'''
    }

    void "test count"() {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            criteriaQuery.select(criteriaBuilder.count(entityRoot))
            def predicateQuery = getQuery(criteriaQuery)

        expect:
            predicateQuery == '''[{$count:'result'}]'''
    }

    @Unroll
    void "test delete"(DeleteSpecification specification) {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaDelete)
            def predicate = specification.toPredicate(entityRoot, criteriaDelete, criteriaBuilder)
            if (predicate) {
                criteriaDelete.where(predicate)
            }
            String predicateQuery = getQuery(criteriaDelete)

        expect:
            predicateQuery == expectedQuery

        where:
            specification << [
                    { root, query, cb ->
                        cb.ge(root.get("amount"), 1000)
                    } as DeleteSpecification,
            ]
            expectedQuery << [
                    '''{amount:{$gte:{$mn_qp:0}}}''',
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
            String predicateQuery = getQuery(criteriaUpdate)
            String updateQuery = getUpdateQuery(criteriaUpdate)

        expect:
            predicateQuery == expectedPredicateQuery
            updateQuery == expectedUpdateQuery
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
                        cb.lessThan(root.get("amount"), 1000)
                    } as UpdateSpecification,
            ]
            expectedPredicateQuery << [
                    '''{amount:{$gte:{$mn_qp:0}}}''',
                    '''{amount:{$lt:{$mn_qp:0}}}''',
            ]
            expectedUpdateQuery << [
                    '''{$set:{name:{$mn_qp:1},amount:{$mn_qp:2}}}''',
                    '''{$set:{name:{$mn_qp:1},amount:{$mn_qp:2}}}''',
            ]
    }

    @Unroll
    void "test #predicate predicate produces where query: #expectedWhereQuery"() {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            criteriaQuery.where(criteriaBuilder."$predicate"(entityRoot.get(property)))
            def predicateQuery = getQuery(criteriaQuery)

        expect:
            predicateQuery == expectedWhereQuery

        where:
            property   | predicate          | expectedWhereQuery
            "enabled"  | "isTrue"           | '{enabled:{$eq:true}}'
            "enabled"  | "isFalse"          | '{enabled:{$eq:false}}'
            "enabled"  | "isNull"           | '{enabled:{$eq:null}}'
            "enabled"  | "isNotNull"        | '{enabled:{$ne:null}}'
            "name"     | "isNotNull"        | '{name:{$ne:null}}'
    }

    @Unroll
    void "test properties #predicate predicate produces where query: #expectedWhereQuery"() {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            criteriaQuery.where(criteriaBuilder."$predicate"(entityRoot.get(property1), entityRoot.get(property2)))
            def predicateQuery = getQuery(criteriaQuery)

        expect:
            predicateQuery == expectedWhereQuery

        where:
            property1 | property2  | predicate              | expectedWhereQuery
            "enabled" | "enabled2" | "equal"                | '''{$expr:{$eq:['$enabled','$enabled2']}}'''
            "enabled" | "enabled2" | "notEqual"             | '''{$expr:{$ne:['$enabled','$enabled2']}}'''
            "enabled" | "enabled2" | "greaterThan"          | '''{$expr:{$gt:['$enabled','$enabled2']}}'''
            "enabled" | "enabled2" | "greaterThanOrEqualTo" | '''{$expr:{$gte:['$enabled','$enabled2']}}'''
            "enabled" | "enabled2" | "lessThan"             | '''{$expr:{$lt:['$enabled','$enabled2']}}'''
            "enabled" | "enabled2" | "lessThanOrEqualTo"    | '''{$expr:{$lte:['$enabled','$enabled2']}}'''
            "amount"  | "budget"   | "gt"                   | '''{$expr:{$gt:['$amount','$budget']}}'''
            "amount"  | "budget"   | "ge"                   | '''{$expr:{$gte:['$amount','$budget']}}'''
            "amount"  | "budget"   | "lt"                   | '''{$expr:{$lt:['$amount','$budget']}}'''
            "amount"  | "budget"   | "le"                   | '''{$expr:{$lte:['$amount','$budget']}}'''
    }

    @Unroll
    void "test property value #predicate predicate produces where query: #expectedWhereQuery"() {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            criteriaQuery.where(criteriaBuilder."$predicate"(entityRoot.get(property1), value))
            def predicateQuery = getQuery(criteriaQuery)

        expect:
            predicateQuery == expectedWhereQuery

        where:
            property1 | value                   | predicate              | expectedWhereQuery
            "enabled" | true                    | "equal"                | '{enabled:{$eq:{$mn_qp:0}}}'
            "enabled" | true                    | "notEqual"             | '{enabled:{$ne:{$mn_qp:0}}}'
            "enabled" | true                    | "greaterThan"          | '{enabled:{$gt:{$mn_qp:0}}}'
            "enabled" | true                    | "greaterThanOrEqualTo" | '{enabled:{$gte:{$mn_qp:0}}}'
            "enabled" | true                    | "lessThan"             | '{enabled:{$lt:{$mn_qp:0}}}'
            "enabled" | true                    | "lessThanOrEqualTo"    | '{enabled:{$lte:{$mn_qp:0}}}'
            "amount"  | BigDecimal.valueOf(100) | "gt"                   | '{amount:{$gt:{$mn_qp:0}}}'
            "amount"  | BigDecimal.valueOf(100) | "ge"                   | '{amount:{$gte:{$mn_qp:0}}}'
            "amount"  | BigDecimal.valueOf(100) | "lt"                   | '{amount:{$lt:{$mn_qp:0}}}'
            "amount"  | BigDecimal.valueOf(100) | "le"                   | '{amount:{$lte:{$mn_qp:0}}}'
    }

    private static String getQuery(PersistentEntityCriteriaQuery<Object> query) {
        return query.build(AnnotationMetadata.EMPTY_METADATA, new NitriteQueryBuilder()).getQuery()
    }

    private static String getQuery(PersistentEntityCriteriaDelete<Object> query) {
        return query.build(AnnotationMetadata.EMPTY_METADATA, new NitriteQueryBuilder()).getQuery()
    }

    private static String getQuery(PersistentEntityCriteriaUpdate<Object> query) {
        return query.build(AnnotationMetadata.EMPTY_METADATA, new NitriteQueryBuilder()).getQuery()
    }

    private static String getUpdateQuery(PersistentEntityCriteriaUpdate<Object> query) {
        return query.build(AnnotationMetadata.EMPTY_METADATA, new NitriteQueryBuilder()).getUpdate()
    }

    @CompileStatic
    interface QuerySpecification<T> {
        Predicate toPredicate(@NonNull Root<T> root, @NonNull CriteriaQuery<?> query, @NonNull CriteriaBuilder criteriaBuilder);
    }

    @CompileStatic
    interface DeleteSpecification<T> {
        Predicate toPredicate(@NonNull Root<T> root, @NonNull CriteriaDelete<?> query, @NonNull CriteriaBuilder criteriaBuilder);
    }

    @CompileStatic
    interface UpdateSpecification<T> {
        Predicate toPredicate(@NonNull Root<T> root, @NonNull CriteriaUpdate<?> query, @NonNull CriteriaBuilder criteriaBuilder);
    }

}
