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
package io.micronaut.data.tck.tests

import groovy.transform.CompileStatic
import io.micronaut.core.annotation.NonNull
import io.micronaut.data.model.jpa.criteria.*
import io.micronaut.data.model.jpa.criteria.impl.QueryResultPersistentEntityCriteriaQuery
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import jakarta.persistence.criteria.*
import spock.lang.Specification
import spock.lang.Unroll

abstract class AbstractCriteriaSpec extends Specification {

    abstract PersistentEntityCriteriaBuilder getCriteriaBuilder()

    abstract PersistentEntityCriteriaQuery getCriteriaQuery()

    abstract PersistentEntityCriteriaDelete getCriteriaDelete()

    abstract PersistentEntityCriteriaUpdate getCriteriaUpdate()

    abstract PersistentEntityRoot createRoot(CriteriaQuery query);

    abstract PersistentEntityRoot createRoot(CriteriaDelete query);

    abstract PersistentEntityRoot createRoot(CriteriaUpdate query);

    @Unroll
    void "test joins"(Specification specification) {
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
                        root.join("others", JoinType.INNER)
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
                    'SELECT test_."id",test_."name",test_."enabled2",test_."enabled",test_."age",test_."amount",test_."budget" FROM "test" test_ INNER JOIN "other_entity" test_others_ ON test_."id"=test_others_."test_id"'
            ]
    }

    @Unroll
    void "test #predicate predicate produces where query: #expectedWhereQuery"() {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            criteriaQuery.where(predicateProp(predicate, entityRoot, property))
            def whereSqlQuery = getWhereQueryPart(criteriaQuery)

        expect:
            whereSqlQuery == expectedWhereQuery

        where:
            property   | predicate   | expectedWhereQuery
            "enabled"  | "isTrue"    | '(test_."enabled" = TRUE)'
            "enabled2" | "isTrue"    | '(test_."enabled2" = TRUE)'
            "enabled"  | "isFalse"   | '(test_."enabled" = FALSE)'
            "enabled2" | "isFalse"   | '(test_."enabled2" = FALSE)'
            "enabled"  | "isNull"    | '(test_."enabled" IS NULL)'
            "enabled2" | "isNull"    | '(test_."enabled2" IS NULL)'
            "enabled"  | "isNotNull" | '(test_."enabled" IS NOT NULL)'
            "enabled2" | "isNotNull" | '(test_."enabled2" IS NOT NULL)'
            "name"     | "isNotNull" | '(test_."name" IS NOT NULL)'
    }

    @Unroll
    void "test not #predicate predicate produces where query: #expectedWhereQuery"() {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            criteriaQuery.where(predicateProp(predicate, entityRoot, property).not())
            def whereSqlQuery = getWhereQueryPart(criteriaQuery)

        expect:
            whereSqlQuery == expectedWhereQuery

        where:
            property   | predicate   | expectedWhereQuery
            "enabled"  | "isTrue"    | '(test_."enabled" = FALSE)'
            "enabled2" | "isTrue"    | '(test_."enabled2" = FALSE)'
            "enabled"  | "isFalse"   | '(test_."enabled" = TRUE)'
            "enabled2" | "isFalse"   | '(test_."enabled2" = TRUE)'
            "enabled"  | "isNull"    | '(test_."enabled" IS NOT NULL)'
            "enabled2" | "isNull"    | '(test_."enabled2" IS NOT NULL)'
            "enabled"  | "isNotNull" | '(test_."enabled" IS NULL)'
            "enabled2" | "isNotNull" | '(test_."enabled2" IS NULL)'
            "name"     | "isNotNull" | '(test_."name" IS NULL)'
    }

    @Unroll
    void "test properties #predicate predicate produces where query: #expectedWhereQuery"() {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            criteriaQuery.where(predicateProps(predicate, entityRoot, property1, property2))
            def whereSqlQuery = getWhereQueryPart(criteriaQuery)

        expect:
            whereSqlQuery == expectedWhereQuery

        where:
            property1 | property2  | predicate              | expectedWhereQuery
            "enabled" | "enabled2" | "equal"                | '(test_."enabled"=test_."enabled2")'
            "enabled" | "enabled2" | "notEqual"             | '(test_."enabled"!=test_."enabled2")'
            "enabled" | "enabled2" | "greaterThan"          | '(test_."enabled">test_."enabled2")'
            "enabled" | "enabled2" | "greaterThanOrEqualTo" | '(test_."enabled">=test_."enabled2")'
            "enabled" | "enabled2" | "lessThan"             | '(test_."enabled"<test_."enabled2")'
            "enabled" | "enabled2" | "lessThanOrEqualTo"    | '(test_."enabled"<=test_."enabled2")'
            "amount"  | "budget"   | "gt"                   | '(test_."amount">test_."budget")'
            "amount"  | "budget"   | "ge"                   | '(test_."amount">=test_."budget")'
            "amount"  | "budget"   | "lt"                   | '(test_."amount"<test_."budget")'
            "amount"  | "budget"   | "le"                   | '(test_."amount"<=test_."budget")'
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
    void "test #projection projection produces selection: #expectedSelectQuery"() {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            criteriaQuery.select(project(projection, entityRoot, property))
            String selectSqlQuery = getSelectQueryPart(criteriaQuery)

        expect:
            selectSqlQuery == expectedSelectQuery

        where:
            property | projection      | expectedSelectQuery
            "age"    | "sum"           | 'SUM(test_."age")'
            "age"    | "avg"           | 'AVG(test_."age")'
            "age"    | "max"           | 'MAX(test_."age")'
            "age"    | "min"           | 'MIN(test_."age")'
// TODO:            "age"    | "count"    | 'COUNT(test_."age")'
            "age"    | "countDistinct" | 'COUNT(DISTINCT(test_."age"))'
    }

    /**
     * Currently ArrayContains criteria is supported only by Azure Cosmos Db and MongoDB.
     * If we introduce more criteria not supported by default then we can test it here.
     */
    void "test unsupported criteria"() {
        given:
        PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
        Specification arrayContainsSpecification = {
            root, query, cb ->
                def parameter = cb.parameter(String)
                ((PersistentEntityCriteriaBuilder)cb).arrayContains(root.get("name"), parameter)
        } as Specification
        def arrayContainsPredicate = arrayContainsSpecification.toPredicate(entityRoot, criteriaQuery, criteriaBuilder)
        when:
        criteriaQuery.where(arrayContainsPredicate)
        getSqlQuery(criteriaQuery)
        then:
        Throwable ex = thrown()
        ex.message.contains('ArrayContains is not supported by this implementation')
    }

    protected Selection project(String projection, PersistentEntityRoot root,  String property) {
        criteriaBuilder."$projection"(root.get(property))
    }

    protected Predicate predicateValue(String predicate, PersistentEntityRoot root, String property, Object value) {
        criteriaBuilder."$predicate"(root.get(property), value)
    }

    protected Predicate predicateProp(String predicate, PersistentEntityRoot root, String property1) {
        criteriaBuilder."$predicate"(root.get(property1))
    }

    protected Predicate predicateProps(String predicate, PersistentEntityRoot root, String property1, String property2) {
        criteriaBuilder."$predicate"(root.get(property1), root.get(property2))
    }

    private static String getSelectQueryPart(PersistentEntityCriteriaQuery<Object> query) {
        def sqlQuery = getSqlQuery(query)
        return sqlQuery.substring("SELECT ".length(), sqlQuery.indexOf(" FROM"))
    }

    private static String getWhereQueryPart(PersistentEntityCriteriaQuery<Object> query) {
        def sqlQuery = getSqlQuery(query)
        return sqlQuery.substring(sqlQuery.indexOf("WHERE ") + 6)
    }

    private static String getSqlQuery(def query) {
        return ((QueryResultPersistentEntityCriteriaQuery) query).buildQuery(new SqlQueryBuilder()).getQuery()
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
