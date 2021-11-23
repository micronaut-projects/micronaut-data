/*
 * Copyright 2017-2021 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.document.mongodb

import groovy.transform.CompileStatic
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.NonNull
import io.micronaut.data.document.model.query.builder.MongoQueryBuilder
import io.micronaut.data.document.mongodb.entities.Test
import io.micronaut.data.document.tck.entities.Settlement
import io.micronaut.data.document.tck.entities.SettlementPk
import io.micronaut.data.event.EntityEventListener
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaBuilder
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaDelete
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQuery
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaUpdate
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot
import io.micronaut.data.model.jpa.criteria.impl.QueryResultPersistentEntityCriteriaQuery
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.model.runtime.RuntimePersistentProperty
import io.micronaut.data.runtime.criteria.RuntimeCriteriaBuilder
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaDelete
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.CriteriaUpdate
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import spock.lang.Specification
import spock.lang.Unroll

class MongoCriteriaSpec extends Specification {

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

    PersistentEntityRoot createRoot(CriteriaQuery query) {
        return query.from(Test)
    }

    PersistentEntityRoot createRoot(CriteriaDelete query) {
        return query.from(Test)
    }

    PersistentEntityRoot createRoot(CriteriaUpdate query) {
        return query.from(Test)
    }

    void "test embedded predicate"(Specification specification) {
        given:
            PersistentEntityCriteriaQuery criteriaQuery = criteriaBuilder.createQuery()
            PersistentEntityRoot entityRoot = criteriaQuery.from(Settlement)
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
                        cb.equal(root.get("id"), cb.parameter(SettlementPk))
                    } as Specification
            ]
            expectedWhereQuery << [
                    '''{'_id.code':{$eq:{$qpidx:0}},'_id.code_id':{$eq:{$qpidx:1}},'_id.county._id.id':{$eq:{$qpidx:2}},'_id.county._id.state_id._id':{$eq:{$qpidx:3}}}''',
            ]
    }

    @Unroll
    void "test criteria predicate"(Specification specification) {
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
                        cb.isTrue(root.get("enabled"))
                    } as Specification,
                    { root, query, cb ->
                        cb.and(cb.isTrue(root.get("enabled")), cb.isTrue(root.get("enabled")))
                    } as Specification,
                    { root, query, cb ->
                        root.get("name").in("A", "B", "C")
                    } as Specification,
                    { root, query, cb ->
                        root.get("name").in("A", "B", "C").not()
                    } as Specification
            ]
            expectedWhereQuery << [
                    '{$and:[{enabled:{$gte:true}},{enabled:{$lte:false}}]}',
                    '{$and:[{amount:{$gte:{$qpidx:0}}},{amount:{$lte:{$qpidx:1}}}]}',
                    '{enabled:{$eq:true}}',
                    '[{$match:{enabled:{$eq:true}}},{$sort:{amount:-1,budget:1}}]',
                    '{enabled:{$eq:true}}',
                    '{$and:[{enabled:{$eq:true}},{enabled:{$eq:true}}]}',
                    '''{name:{$in:['A','B','C']}}''',
                    '''{name:{$nin:['A','B','C']}}''',
            ]
    }

    @Unroll
    void "test joins"(Specification specification) {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            def predicate = specification.toPredicate(entityRoot, criteriaQuery, criteriaBuilder)
            if (predicate) {
                criteriaQuery.where(predicate)
            }
            String q = getQuery(criteriaQuery)

        expect:
            q == expectedQuery

        where:
            specification << [
                    { root, query, cb ->
                        def othersJoin = root.join("others")
                        cb.and(cb.equal(root.get("amount"), othersJoin.get("amount")))
                    } as Specification,

                    { root, query, cb ->
                        def othersJoin = root.join("others")
                        def simpleJoin = othersJoin.join("simple")
                        cb.and(
                                cb.equal(root.get("amount"), othersJoin.get("amount")),
                                cb.equal(root.get("amount"), simpleJoin.get("amount")),
                        )
                    } as Specification,
                    { root, query, cb ->
                        def oneOthersJoin = root.join("oneOther")
                        cb.equal(oneOthersJoin.get("name"), "xyz")
                    } as Specification,
                    { root, query, cb ->
                        def oneOthersJoin = root.join("manyToOneOther")
                        cb.equal(oneOthersJoin.get("name"), "xyz")
                    } as Specification
            ]
            expectedQuery << [
                    '''[{$lookup:{from:'other_entity',localField:'_id',foreignField:'test._id',as:'others'}},{$match:{$expr:{$eq:['$amount','$others.amount']}}}]''',
                    '''[{$lookup:{from:'other_entity',localField:'_id',foreignField:'test._id',pipeline:[{$lookup:{from:'simple_entity',localField:'simple._id',foreignField:'_id',as:'simple'}},{$unwind:{path:'$simple',preserveNullAndEmptyArrays:true}}],as:'others'}},{$match:{$and:[{$expr:{$eq:['$amount','$others.amount']}},{$expr:{$eq:['$amount','$others.simple.amount']}}]}}]''',
                    '''[{$lookup:{from:'other_entity',localField:'oneOther._id',foreignField:'_id',as:'oneOther'}},{$unwind:{path:'$oneOther',preserveNullAndEmptyArrays:true}},{$match:{'oneOther.name':{$eq:'xyz'}}}]''',
                    '''[{$lookup:{from:'other_entity',localField:'manyToOneOther._id',foreignField:'_id',as:'manyToOneOther'}},{$unwind:{path:'$manyToOneOther',preserveNullAndEmptyArrays:true}},{$match:{'manyToOneOther.name':{$eq:'xyz'}}}]'''
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
            "age"    | "max"      | '''[{$group:{age:{$max:'$age'},'_id':null}}]'''
            "age"    | "min"      | '''[{$group:{age:{$min:'$age'},'_id':null}}]'''
            "age"    | "avg"      | '''[{$group:{age:{$avg:'$age'},'_id':null}}]'''
            "age"    | "sum"      | '''[{$group:{age:{$sum:'$age'},'_id':null}}]'''
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
                    '''{amount:{$gte:1000}}''',
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
                        cb.ge(root.get("amount"), 1000)
                    } as UpdateSpecification,
                    { root, query, cb ->
                        query.set("name", "test")
                        query.set(root.get("amount"), cb.parameter(Integer))
                        cb.ge(root.get("amount"), 1000)
                    } as UpdateSpecification,
            ]
            expectedPredicateQuery << [
                    '''{amount:{$gte:1000}}''',
                    '''{amount:{$gte:1000}}''',
                    '''{amount:{$gte:1000}}''',
            ]
            expectedUpdateQuery << [
                    '''{$set:{name:'ABC',amount:123}}''',
                    '''{$set:{name:{$qpidx:0},amount:{$qpidx:1}}}''',
                    '''{$set:{name:'test',amount:{$qpidx:0}}}''',
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
            "enabled2" | "isTrue"           | '{enabled2:{$eq:true}}'
            "enabled"  | "isFalse"          | '{enabled:{$eq:false}}'
            "enabled2" | "isFalse"          | '{enabled2:{$eq:false}}'
            "enabled"  | "isNull"           | '{enabled:{$eq:null}}'
            "enabled2" | "isNull"           | '{enabled2:{$eq:null}}'
            "enabled"  | "isNotNull"        | '{enabled:{$ne:null}}'
            "enabled2" | "isNotNull"        | '{enabled2:{$ne:null}}'
            "name"     | "isNotNull"        | '{name:{$ne:null}}'
            "name"     | "isEmptyString"    | '''{$or:[{name:{$eq:''}},{name:{$exists:false}}]}'''
            "name"     | "isNotEmptyString" | '''{$and:[{name:{$ne:''}},{name:{$exists:true}}]}'''
    }

    @Unroll
    void "test not #predicate predicate produces where query: #expectedWhereQuery"() {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            criteriaQuery.where(criteriaBuilder."$predicate"(entityRoot.get(property)).not())
            def predicateQuery = getQuery(criteriaQuery)

        expect:
            predicateQuery == expectedWhereQuery

        where:
            property   | predicate   | expectedWhereQuery
            "enabled"  | "isTrue"    | '{enabled:{$eq:false}}'
            "enabled2" | "isTrue"    | '{enabled2:{$eq:false}}'
            "enabled"  | "isFalse"   | '{enabled:{$eq:true}}'
            "enabled2" | "isFalse"   | '{enabled2:{$eq:true}}'
            "enabled"  | "isNull"    | '{enabled:{$ne:null}}'
            "enabled2" | "isNull"    | '{enabled2:{$ne:null}}'
            "enabled"  | "isNotNull" | '{enabled:{$eq:null}}'
            "enabled2" | "isNotNull" | '{enabled2:{$eq:null}}'
            "name"     | "isNotNull" | '{name:{$eq:null}}'
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
    void "test properties not #predicate predicate produces where query: #expectedWhereQuery"() {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            criteriaQuery.where(criteriaBuilder."$predicate"(entityRoot.get(property1), entityRoot.get(property2)).not())
            def predicateQuery = getQuery(criteriaQuery)

        expect:
            predicateQuery == expectedWhereQuery

        where:
            property1 | property2  | predicate              | expectedWhereQuery
            "enabled" | "enabled2" | "equal"                | '''{$expr:{$ne:['$enabled','$enabled2']}}'''
            "enabled" | "enabled2" | "notEqual"             | '''{$expr:{$eq:['$enabled','$enabled2']}}'''
            "enabled" | "enabled2" | "greaterThan"          | '''{$expr:{$not:{$gt:['$enabled','$enabled2']}}}'''
            "enabled" | "enabled2" | "greaterThanOrEqualTo" | '''{$expr:{$not:{$gte:['$enabled','$enabled2']}}}'''
            "enabled" | "enabled2" | "lessThan"             | '''{$expr:{$not:{$lt:['$enabled','$enabled2']}}}'''
            "enabled" | "enabled2" | "lessThanOrEqualTo"    | '''{$expr:{$not:{$lte:['$enabled','$enabled2']}}}'''
            "amount"  | "budget"   | "gt"                   | '''{$expr:{$not:{$gt:['$amount','$budget']}}}'''
            "amount"  | "budget"   | "ge"                   | '''{$expr:{$not:{$gte:['$amount','$budget']}}}'''
            "amount"  | "budget"   | "lt"                   | '''{$expr:{$not:{$lt:['$amount','$budget']}}}'''
            "amount"  | "budget"   | "le"                   | '''{$expr:{$not:{$lte:['$amount','$budget']}}}'''
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
            "enabled" | true                    | "equal"                | '{enabled:{$eq:true}}'
            "enabled" | true                    | "notEqual"             | '{enabled:{$ne:true}}'
            "enabled" | true                    | "greaterThan"          | '{enabled:{$gt:true}}'
            "enabled" | true                    | "greaterThanOrEqualTo" | '{enabled:{$gte:true}}'
            "enabled" | true                    | "lessThan"             | '{enabled:{$lt:true}}'
            "enabled" | true                    | "lessThanOrEqualTo"    | '{enabled:{$lte:true}}'
            "amount"  | BigDecimal.valueOf(100) | "gt"                   | '{amount:{$gt:100}}'
            "amount"  | BigDecimal.valueOf(100) | "ge"                   | '{amount:{$gte:100}}'
            "amount"  | BigDecimal.valueOf(100) | "lt"                   | '{amount:{$lt:100}}'
            "amount"  | BigDecimal.valueOf(100) | "le"                   | '{amount:{$lte:100}}'
    }

    @Unroll
    void "test property value not #predicate predicate produces where query: #expectedWhereQuery"() {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            criteriaQuery.where(criteriaBuilder."$predicate"(entityRoot.get(property1), value).not())
            def predicateQuery = getQuery(criteriaQuery)

        expect:
            predicateQuery == expectedWhereQuery

        where:
            property1 | value                   | predicate              | expectedWhereQuery
            "enabled" | true                    | "equal"                | '{enabled:{$ne:true}}'
            "enabled" | true                    | "notEqual"             | '{enabled:{$eq:true}}'
            "enabled" | true                    | "greaterThan"          | '{enabled:{$not:{$gt:true}}}'
            "enabled" | true                    | "greaterThanOrEqualTo" | '{enabled:{$not:{$gte:true}}}'
            "enabled" | true                    | "lessThan"             | '{enabled:{$not:{$lt:true}}}'
            "enabled" | true                    | "lessThanOrEqualTo"    | '{enabled:{$not:{$lte:true}}}'
            "amount"  | BigDecimal.valueOf(100) | "gt"                   | '{amount:{$not:{$gt:100}}}'
            "amount"  | BigDecimal.valueOf(100) | "ge"                   | '{amount:{$not:{$gte:100}}}'
            "amount"  | BigDecimal.valueOf(100) | "lt"                   | '{amount:{$not:{$lt:100}}}'
            "amount"  | BigDecimal.valueOf(100) | "le"                   | '{amount:{$not:{$lte:100}}}'
    }

    private static String getQuery(PersistentEntityCriteriaQuery<Object> query) {
        return ((QueryResultPersistentEntityCriteriaQuery) query).buildQuery(new MongoQueryBuilder()).getQuery()
    }

    private static String getQuery(PersistentEntityCriteriaDelete<Object> query) {
        return ((QueryResultPersistentEntityCriteriaQuery) query).buildQuery(new MongoQueryBuilder()).getQuery()
    }

    private static String getQuery(PersistentEntityCriteriaUpdate<Object> query) {
        return ((QueryResultPersistentEntityCriteriaQuery) query).buildQuery(new MongoQueryBuilder()).getQuery()
    }

    private static String getUpdateQuery(PersistentEntityCriteriaUpdate<Object> query) {
        return ((QueryResultPersistentEntityCriteriaQuery) query).buildQuery(new MongoQueryBuilder()).getUpdate()
    }

    @CompileStatic
    interface Specification<T> {
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
