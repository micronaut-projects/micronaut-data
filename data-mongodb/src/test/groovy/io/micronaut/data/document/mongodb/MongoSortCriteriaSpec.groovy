/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.data.document.model.query.builder.MongoQueryBuilder
import io.micronaut.data.document.mongodb.entities.Test
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaBuilder
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQuery
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot
import io.micronaut.data.model.jpa.criteria.impl.expression.LiteralExpression
import io.micronaut.data.runtime.criteria.RuntimeCriteriaBuilder
import jakarta.persistence.criteria.Nulls
import spock.lang.Specification

/**
 * Covers how MongoDB sorts are built. MongoDB can only sort against a field, so both an explicit
 * null ordering and an ordering over a computed expression need a field computing first.
 */
class MongoSortCriteriaSpec extends Specification {

    void "test null ordering ranks null and missing values"() {
        expect: "nulls asked to sort last are ranked after everything else"
        orderedBy { cb, root -> cb.sort(root.get("name"), true, false, Nulls.LAST) } ==
                "[{\$addFields:{__micronaut_nulls_0:{\$cond:[{\$in:[{\$type:'\$name'},['missing','null']]},1,0]}}}," +
                "{\$sort:{__micronaut_nulls_0:1,name:1}}," +
                "{\$unset:['__micronaut_nulls_0']}]"

        and: "nulls first is ranked the other way round, independently of the sort direction"
        orderedBy { cb, root -> cb.sort(root.get("name"), false, false, Nulls.FIRST) } ==
                "[{\$addFields:{__micronaut_nulls_0:{\$cond:[{\$in:[{\$type:'\$name'},['missing','null']]},0,1]}}}," +
                "{\$sort:{__micronaut_nulls_0:1,name:-1}}," +
                "{\$unset:['__micronaut_nulls_0']}]"

        and: "an unspecified null ordering leaves the sort alone"
        orderedBy { cb, root -> cb.sort(root.get("name"), true, false, Nulls.NONE) } == "[{\$sort:{name:1}}]"
    }

    void "test ordering by an arithmetic expression computes a field to sort on"() {
        expect:
        ascendingBy { cb, root -> cb.sum(root.get("amount"), root.get("budget")) } ==
                computedSort("\$add:['\$amount','\$budget']")
        ascendingBy { cb, root -> cb.diff(root.get("amount"), root.get("budget")) } ==
                computedSort("\$subtract:['\$amount','\$budget']")
        ascendingBy { cb, root -> cb.prod(root.get("amount"), root.get("budget")) } ==
                computedSort("\$multiply:['\$amount','\$budget']")
        ascendingBy { cb, root -> cb.quot(root.get("amount"), root.get("budget")) } ==
                computedSort("\$divide:['\$amount','\$budget']")
        ascendingBy { cb, root -> cb.concat(root.get("name"), root.get("name")) } ==
                computedSort("\$concat:['\$name','\$name']")
    }

    void "test ordering by a string expression computes a field to sort on"() {
        expect:
        ascendingBy { cb, root -> cb.lower(root.get("name")) } == computedSort("\$toLower:'\$name'")
        ascendingBy { cb, root -> cb.upper(root.get("name")) } == computedSort("\$toUpper:'\$name'")
        ascendingBy { cb, root -> cb.length(root.get("name")) } == computedSort("\$strLenCP:'\$name'")
    }

    void "test ordering by the left and right functions uses a substring"() {
        expect: "left takes the leading characters"
        ascendingBy { cb, root -> cb.function("LEFT", String, root.get("name"), cb.literal(2)) } ==
                computedSort("\$substrCP:['\$name',0,{\$mn_qp:0}]")

        and: "right offsets by the length of the value, binding the same parameter once"
        ascendingBy { cb, root -> cb.function("RIGHT", String, root.get("name"), cb.literal(2)) } ==
                computedSort("\$substrCP:['\$name',{\$subtract:[{\$strLenCP:'\$name'},{\$mn_qp:0}]},{\$mn_qp:0}]")
    }

    void "test a descending expression order keeps the direction"() {
        expect:
        orderedBy { cb, root -> cb.desc(cb.quot(root.get("amount"), root.get("budget"))) } ==
                "[{\$addFields:{__micronaut_sort_0:{\$divide:['\$amount','\$budget']}}}," +
                "{\$sort:{__micronaut_sort_0:-1}}," +
                "{\$unset:['__micronaut_sort_0']}]"
    }

    void "test an expression and a null ordering are computed by separate stages"() {
        expect: "a computed field cannot be ranked by the stage that defines it"
        orderedBy { cb, root -> cb.sort(cb.lower(root.get("name")), true, false, Nulls.LAST) } ==
                "[{\$addFields:{__micronaut_sort_0:{\$toLower:'\$name'}}}," +
                "{\$addFields:{__micronaut_nulls_0:{\$cond:[{\$in:[{\$type:'\$__micronaut_sort_0'},['missing','null']]},1,0]}}}," +
                "{\$sort:{__micronaut_nulls_0:1,__micronaut_sort_0:1}}," +
                "{\$unset:['__micronaut_sort_0','__micronaut_nulls_0']}]"
    }

    void "test a computed field and a null rank over a plain property share one stage"() {
        expect: "only a rank over a computed field needs a stage of its own"
        orderedBy { cb, root ->
            [cb.asc(cb.lower(root.get("name"))), cb.sort(root.get("age"), true, false, Nulls.LAST)]
        } == "[{\$addFields:{__micronaut_sort_0:{\$toLower:'\$name'}," +
                "__micronaut_nulls_0:{\$cond:[{\$in:[{\$type:'\$age'},['missing','null']]},1,0]}}}," +
                "{\$sort:{__micronaut_sort_0:1,__micronaut_nulls_0:1,age:1}}," +
                "{\$unset:['__micronaut_sort_0','__micronaut_nulls_0']}]"
    }

    void "test ordering by an expression over a literal inlines the literal"() {
        expect:
        ascendingBy { cb, root -> cb.prod(root.get("amount"), new LiteralExpression<Integer>(2)) } ==
                computedSort("\$multiply:['\$amount',2]")
    }

    void "test ordering by a null literal is rejected"() {
        when:
        ascendingBy { cb, root -> cb.prod(root.get("amount"), new LiteralExpression<Integer>((Integer) null)) }

        then:
        def e = thrown(UnsupportedOperationException)
        e.message.contains("null literal")
    }

    void "test ordering by an unsupported function is rejected"() {
        when:
        ascendingBy { cb, root -> cb.function("SOUNDEX", String, root.get("name")) }

        then:
        def e = thrown(UnsupportedOperationException)
        e.message.contains("SOUNDEX")
    }

    void "test ordering by an unsupported expression is rejected"() {
        when:
        ascendingBy { cb, root -> cb.count(root) }

        then:
        def e = thrown(UnsupportedOperationException)
        e.message.contains("is not supported by Micronaut Data MongoDB")
    }

    private static String computedSort(String expression) {
        "[{\$addFields:{__micronaut_sort_0:{$expression}}},{\$sort:{__micronaut_sort_0:1}},{\$unset:['__micronaut_sort_0']}]"
    }

    private static String ascendingBy(Closure<?> expression) {
        orderedBy { cb, root -> cb.asc(expression.call(cb, root)) }
    }

    private static String orderedBy(Closure<?> order) {
        PersistentEntityCriteriaBuilder criteriaBuilder = new RuntimeCriteriaBuilder()
        PersistentEntityCriteriaQuery criteriaQuery = criteriaBuilder.createQuery()
        PersistentEntityRoot entityRoot = criteriaQuery.from(Test)
        def orders = order.call(criteriaBuilder, entityRoot)
        criteriaQuery.orderBy(orders instanceof List ? orders : [orders])
        criteriaQuery.build(AnnotationMetadata.EMPTY_METADATA, new MongoQueryBuilder()).getQuery()
    }
}
