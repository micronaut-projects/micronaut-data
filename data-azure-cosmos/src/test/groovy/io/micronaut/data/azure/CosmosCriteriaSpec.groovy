/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.azure

import groovy.transform.CompileStatic
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.NonNull
import io.micronaut.data.azure.entities.CosmosBook
import io.micronaut.data.azure.entities.Family
import io.micronaut.data.document.model.query.builder.CosmosSqlQueryBuilder
import io.micronaut.data.document.tck.entities.Settlement
import io.micronaut.data.document.tck.entities.SettlementPk
import io.micronaut.data.event.EntityEventListener
import io.micronaut.data.model.jpa.criteria.*
import io.micronaut.data.model.jpa.criteria.impl.QueryResultPersistentEntityCriteriaQuery
import io.micronaut.data.model.query.builder.QueryBuilder
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.model.runtime.RuntimePersistentProperty
import io.micronaut.data.runtime.criteria.RuntimeCriteriaBuilder
import jakarta.persistence.criteria.*
import spock.lang.Unroll

import java.time.Instant
import java.time.temporal.ChronoUnit

class CosmosCriteriaSpec extends AbstractTypeElementSpec {

    PersistentEntityCriteriaBuilder criteriaBuilder

    PersistentEntityCriteriaQuery criteriaQuery

    static QueryBuilder queryBuilder

    void setupSpec() {
        def annotationMetadata = buildTypeAnnotationMetadata('''
package test;
import io.micronaut.data.cosmos.annotation.CosmosRepository;

@CosmosRepository
interface MyRepository {
}
''')
        queryBuilder = new CosmosSqlQueryBuilder(annotationMetadata)
    }

    void setup() {
        Map<Class, RuntimePersistentEntity> map = new HashMap<>()
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
    }

    @Unroll
    void "test criteria predicate"(Specification specification) {
        given:
            def entityRoot = criteriaQuery.from(Family)
            def predicate = specification.toPredicate(entityRoot, criteriaQuery, criteriaBuilder)
            if (predicate) {
                criteriaQuery.where(predicate)
            }
            String predicateQuery = getQuery(criteriaQuery)
        expect:
            predicateQuery == expectedWhereQuery
        where:
            specification << [
                {
                 root, query, cb ->
                     def gender = new AbstractMap.SimpleImmutableEntry("gender", "male")
                     def childrenJoin = root.join("children")
                     def parameter = cb.literal(gender)
                     ((PersistentEntityCriteriaBuilder)cb).arrayContains(childrenJoin, parameter)
                } as Specification,
                { root, query, cb ->
                    cb.between(root.get("registeredDate"), new Date() , Date.from(Instant.now().plus(1, ChronoUnit.DAYS)))
                } as Specification,
                { root, query, cb ->
                    def parameter = cb.parameter(Integer)
                    cb.between(root.get("lastName"), parameter, parameter)
                } as Specification,
                { root, query, cb ->
                    query.where(root.get("registered"))
                    null
                } as Specification,
                { root, query, cb ->
                    query.where(root.get("registered"))
                    query.orderBy(cb.desc(root.get("registeredDate")), cb.asc(root.get("lastName")))
                    null
                } as Specification,
                { root, query, cb ->
                    cb.isTrue(root.get("registered"))
                } as Specification,
                { root, query, cb ->
                    cb.and(cb.isTrue(root.get("registered")), cb.isTrue(root.get("registered")))
                } as Specification,
                { root, query, cb ->
                    root.get("lastName").in("A", "B", "C")
                } as Specification,
                { root, query, cb ->
                    root.get("lastName").in("A", "B", "C").not()
                } as Specification,
                {
                    root, query, cb ->
                        def parameter = cb.parameter(String)
                        ((PersistentEntityCriteriaBuilder)cb).arrayContains(root.get("tags"), parameter)
                } as Specification
            ]
            expectedWhereQuery << [
                'SELECT DISTINCT VALUE family_ FROM family family_ WHERE (ARRAY_CONTAINS(family_.children,@p1,true))',
                'SELECT DISTINCT VALUE family_ FROM family family_ WHERE ((family_.registeredDate >= @p1 AND family_.registeredDate <= @p2))',
                'SELECT DISTINCT VALUE family_ FROM family family_ WHERE ((family_.lastName >= @p1 AND family_.lastName <= @p2))',
                'SELECT DISTINCT VALUE family_ FROM family family_ WHERE (family_.registered = TRUE)',
                'SELECT DISTINCT VALUE family_ FROM family family_ WHERE (family_.registered = TRUE) ORDER BY family_.registeredDate DESC,family_.lastName ASC',
                'SELECT DISTINCT VALUE family_ FROM family family_ WHERE (family_.registered = TRUE)',
                'SELECT DISTINCT VALUE family_ FROM family family_ WHERE (family_.registered = TRUE AND family_.registered = TRUE)',
                'SELECT DISTINCT VALUE family_ FROM family family_ WHERE (family_.lastName IN (@p1))',
                'SELECT DISTINCT VALUE family_ FROM family family_ WHERE (family_.lastName NOT IN (@p1))',
                'SELECT DISTINCT VALUE family_ FROM family family_ WHERE (ARRAY_CONTAINS(family_.tags,@p1,true))'
            ]
    }

    @Unroll
    void "test projection #projection"() {
        given:
            PersistentEntityRoot entityRoot = criteriaQuery.from(CosmosBook)
            criteriaQuery.select(criteriaBuilder."$projection"(entityRoot.get(property)))
            def predicateQuery = getQuery(criteriaQuery)
        expect:
            predicateQuery == expectedWhereQuery
        where:
            property        | projection | expectedWhereQuery
            "totalPages"    | "max"      | 'SELECT VALUE MAX(cosmos_book_.totalPages) FROM cosmosbook cosmos_book_'
            "totalPages"    | "min"      | 'SELECT VALUE MIN(cosmos_book_.totalPages) FROM cosmosbook cosmos_book_'
            "totalPages"    | "avg"      | 'SELECT VALUE AVG(cosmos_book_.totalPages) FROM cosmosbook cosmos_book_'
            "totalPages"    | "sum"      | 'SELECT VALUE SUM(cosmos_book_.totalPages) FROM cosmosbook cosmos_book_'
    }

    @Unroll
    void "test joins"(Specification specification) {
        given:
            PersistentEntityRoot entityRoot = criteriaQuery.from(Family)
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
                        def addressJoin = root.join("address")
                        cb.equal(addressJoin.get("state"), "NY")
                    } as Specification,

                    { root, query, cb ->
                        def childrenJoin = root.join("children")
                        def petsJoin = childrenJoin.join("pets")
                        cb.and(
                                cb.equal(petsJoin.get("type"), "dog"),
                                cb.equal(petsJoin.get("givenName"), "Snoopy"),
                        )
                    } as Specification
            ]
            expectedQuery << [
                    'SELECT DISTINCT VALUE family_ FROM family family_ WHERE (family_.address.state = @p1)',
                    'SELECT DISTINCT VALUE family_ FROM family family_ JOIN family_children_pets_ IN family_.children.pets WHERE (family_children_pets_.type = @p1 AND family_children_pets_.givenName = @p2)'
            ]
    }

    void "test count"() {
        given:
            PersistentEntityRoot entityRoot = criteriaQuery.from(CosmosBook)
            criteriaQuery.select(criteriaBuilder.count(entityRoot))
            def predicateQuery = getQuery(criteriaQuery)
        expect:
            predicateQuery == 'SELECT VALUE COUNT(1) FROM cosmosbook cosmos_book_'
    }

    @Unroll
    void "test #predicate predicate produces where query: #expectedWhereQuery"() {
        given:
            PersistentEntityRoot entityRoot = criteriaQuery.from(Family)
            criteriaQuery.where(criteriaBuilder."$predicate"(entityRoot.get(property)))
            def predicateQuery = getQuery(criteriaQuery)
        expect:
            predicateQuery == expectedWhereQuery
        where:
            property      | predicate          | expectedWhereQuery
            "registered"  | "isTrue"           | 'SELECT DISTINCT VALUE family_ FROM family family_ WHERE (family_.registered = TRUE)'
            "registered"  | "isFalse"          | 'SELECT DISTINCT VALUE family_ FROM family family_ WHERE (family_.registered = FALSE)'
            "registered"  | "isNull"           | 'SELECT DISTINCT VALUE family_ FROM family family_ WHERE (NOT IS_DEFINED(family_.registered) OR IS_NULL(family_.registered))'
            "registered"  | "isNotNull"        | 'SELECT DISTINCT VALUE family_ FROM family family_ WHERE (IS_DEFINED(family_.registered) AND NOT IS_NULL(family_.registered))'
            "lastName"    | "isNotNull"        | 'SELECT DISTINCT VALUE family_ FROM family family_ WHERE (IS_DEFINED(family_.lastName) AND NOT IS_NULL(family_.lastName))'
            "lastName"    | "isEmptyString"    | 'SELECT DISTINCT VALUE family_ FROM family family_ WHERE (NOT IS_DEFINED(family_.lastName) OR IS_NULL(family_.lastName) OR family_.lastName = \'\')'
            "lastName"    | "isNotEmptyString" | 'SELECT DISTINCT VALUE family_ FROM family family_ WHERE (IS_DEFINED(family_.lastName) AND NOT IS_NULL(family_.lastName) AND family_.lastName != \'\')'
    }

    @Unroll
    void "test property value #predicate predicate produces where query: #expectedWhereQuery"() {
        given:
            PersistentEntityRoot entityRoot = criteriaQuery.from(Family)
            criteriaQuery.where(criteriaBuilder."$predicate"(entityRoot.get(property1), value))
            def predicateQuery = getQuery(criteriaQuery)
        expect:
            predicateQuery == expectedWhereQuery
        where:
            property1         | value                   | predicate              | expectedWhereQuery
            "registered"      | true                    | "equal"                | 'SELECT DISTINCT VALUE family_ FROM family family_ WHERE (family_.registered = @p1)'
            "registered"      | true                    | "notEqual"             | 'SELECT DISTINCT VALUE family_ FROM family family_ WHERE (family_.registered != @p1)'
            "registered"      | true                    | "greaterThan"          | 'SELECT DISTINCT VALUE family_ FROM family family_ WHERE (family_.registered > @p1)'
            "registered"      | true                    | "greaterThanOrEqualTo" | 'SELECT DISTINCT VALUE family_ FROM family family_ WHERE (family_.registered >= @p1)'
            "registered"      | true                    | "lessThan"             | 'SELECT DISTINCT VALUE family_ FROM family family_ WHERE (family_.registered < @p1)'
            "registered"      | true                    | "lessThanOrEqualTo"    | 'SELECT DISTINCT VALUE family_ FROM family family_ WHERE (family_.registered <= @p1)'
    }

    @Unroll
    void "test property value #predicate predicate produces where query (not): #expectedWhereQuery"() {
        given:
            PersistentEntityRoot entityRoot = criteriaQuery.from(Family)
            criteriaQuery.where(criteriaBuilder."$predicate"(entityRoot.get(property1), value).not())
            def predicateQuery = getQuery(criteriaQuery)
        expect:
            predicateQuery == expectedWhereQuery
        where:
            property1         | value                   | predicate              | expectedWhereQuery
            "registered"      | true                    | "equal"                | 'SELECT DISTINCT VALUE family_ FROM family family_ WHERE (family_.registered != @p1)'
            "registered"      | true                    | "notEqual"             | 'SELECT DISTINCT VALUE family_ FROM family family_ WHERE (family_.registered = @p1)'
            "registered"      | true                    | "greaterThan"          | 'SELECT DISTINCT VALUE family_ FROM family family_ WHERE (NOT(family_.registered > @p1))'
            "registered"      | true                    | "greaterThanOrEqualTo" | 'SELECT DISTINCT VALUE family_ FROM family family_ WHERE (NOT(family_.registered >= @p1))'
            "registered"      | true                    | "lessThan"             | 'SELECT DISTINCT VALUE family_ FROM family family_ WHERE (NOT(family_.registered < @p1))'
            "registered"      | true                    | "lessThanOrEqualTo"    | 'SELECT DISTINCT VALUE family_ FROM family family_ WHERE (NOT(family_.registered <= @p1))'
    }

    @Unroll
    void "test property value #predicate predicate produces where query (numeric comparison): #expectedWhereQuery"() {
        given:
            PersistentEntityRoot entityRoot = criteriaQuery.from(CosmosBook)
            criteriaQuery.where(criteriaBuilder."$predicate"(entityRoot.get(property1), value))
            def predicateQuery = getQuery(criteriaQuery)
        expect:
            predicateQuery == expectedWhereQuery
        where:
            property1     | value  | predicate              | expectedWhereQuery
            "totalPages"  | 100    | "gt"                   | 'SELECT DISTINCT VALUE cosmos_book_ FROM cosmosbook cosmos_book_ WHERE (cosmos_book_.totalPages > @p1)'
            "totalPages"  | 120    | "ge"                   | 'SELECT DISTINCT VALUE cosmos_book_ FROM cosmosbook cosmos_book_ WHERE (cosmos_book_.totalPages >= @p1)'
            "totalPages"  | 200    | "lt"                   | 'SELECT DISTINCT VALUE cosmos_book_ FROM cosmosbook cosmos_book_ WHERE (cosmos_book_.totalPages < @p1)'
            "totalPages"  | 80     | "le"                   | 'SELECT DISTINCT VALUE cosmos_book_ FROM cosmosbook cosmos_book_ WHERE (cosmos_book_.totalPages <= @p1)'
    }

    @Unroll
    void "test property value #predicate predicate produces where query (numeric comparison negated): #expectedWhereQuery"() {
        given:
            PersistentEntityRoot entityRoot = criteriaQuery.from(CosmosBook)
            criteriaQuery.where(criteriaBuilder."$predicate"(entityRoot.get(property1), value).not())
            def predicateQuery = getQuery(criteriaQuery)
        expect:
            predicateQuery == expectedWhereQuery
        where:
            property1     | value  | predicate              | expectedWhereQuery
            "totalPages"  | 100    | "gt"                   | 'SELECT DISTINCT VALUE cosmos_book_ FROM cosmosbook cosmos_book_ WHERE (NOT(cosmos_book_.totalPages > @p1))'
            "totalPages"  | 120    | "ge"                   | 'SELECT DISTINCT VALUE cosmos_book_ FROM cosmosbook cosmos_book_ WHERE (NOT(cosmos_book_.totalPages >= @p1))'
            "totalPages"  | 200    | "lt"                   | 'SELECT DISTINCT VALUE cosmos_book_ FROM cosmosbook cosmos_book_ WHERE (NOT(cosmos_book_.totalPages < @p1))'
            "totalPages"  | 80     | "le"                   | 'SELECT DISTINCT VALUE cosmos_book_ FROM cosmosbook cosmos_book_ WHERE (NOT(cosmos_book_.totalPages <= @p1))'
    }

    private static String getQuery(PersistentEntityCriteriaQuery<Object> query) {
        return ((QueryResultPersistentEntityCriteriaQuery) query).buildQuery(queryBuilder).getQuery()
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
