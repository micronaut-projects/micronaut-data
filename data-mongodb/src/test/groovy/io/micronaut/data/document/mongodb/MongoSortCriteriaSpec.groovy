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
import io.micronaut.data.runtime.criteria.RuntimeCriteriaBuilder
import jakarta.persistence.criteria.Nulls
import spock.lang.Specification

/**
 * Covers how MongoDB sorts are built, which MongoDB can only express against a field: an explicit
 * null ordering and an ordering over a computed expression both need a field computing first.
 */
class MongoSortCriteriaSpec extends Specification {

    PersistentEntityCriteriaBuilder criteriaBuilder
    PersistentEntityCriteriaQuery criteriaQuery
    PersistentEntityRoot entityRoot

    void setup() {
        criteriaBuilder = new RuntimeCriteriaBuilder()
        criteriaQuery = criteriaBuilder.createQuery()
        entityRoot = criteriaQuery.from(Test)
    }

    void "test null ordering ranks null and missing values"() {
        when: "nulls are asked to sort last"
        criteriaQuery.orderBy(criteriaBuilder.sort(entityRoot.get("name"), true, false, Nulls.LAST))

        then:
        query == "[{\$addFields:{__micronaut_nulls_0:{\$cond:[{\$in:[{\$type:'\$name'},['missing','null']]},1,0]}}}," +
                "{\$sort:{__micronaut_nulls_0:1,name:1}}," +
                "{\$unset:['__micronaut_nulls_0']}]"
    }

    void "test null ordering first is independent of the sort direction"() {
        when:
        criteriaQuery.orderBy(criteriaBuilder.sort(entityRoot.get("name"), false, false, Nulls.FIRST))

        then:
        query == "[{\$addFields:{__micronaut_nulls_0:{\$cond:[{\$in:[{\$type:'\$name'},['missing','null']]},0,1]}}}," +
                "{\$sort:{__micronaut_nulls_0:1,name:-1}}," +
                "{\$unset:['__micronaut_nulls_0']}]"
    }

    void "test an unspecified null ordering leaves the sort alone"() {
        when:
        criteriaQuery.orderBy(criteriaBuilder.sort(entityRoot.get("name"), true, false, Nulls.NONE))

        then:
        query == "[{\$sort:{name:1}}]"
    }

    void "test ordering by an arithmetic expression computes a field to sort on"() {
        when:
        criteriaQuery.orderBy(criteriaBuilder.desc(criteriaBuilder.quot(entityRoot.get("amount"), entityRoot.get("budget"))))

        then:
        query == "[{\$addFields:{__micronaut_sort_0:{\$divide:['\$amount','\$budget']}}}," +
                "{\$sort:{__micronaut_sort_0:-1}}," +
                "{\$unset:['__micronaut_sort_0']}]"
    }

    void "test ordering by a string function computes a field to sort on"() {
        when:
        criteriaQuery.orderBy(criteriaBuilder.asc(criteriaBuilder.lower(entityRoot.get("name"))))

        then:
        query == "[{\$addFields:{__micronaut_sort_0:{\$toLower:'\$name'}}}," +
                "{\$sort:{__micronaut_sort_0:1}}," +
                "{\$unset:['__micronaut_sort_0']}]"
    }

    void "test an expression and a null ordering are computed by separate stages"() {
        when: "a computed field cannot be ranked by the stage that defines it"
        criteriaQuery.orderBy(criteriaBuilder.sort(criteriaBuilder.lower(entityRoot.get("name")), true, false, Nulls.LAST))

        then:
        query == "[{\$addFields:{__micronaut_sort_0:{\$toLower:'\$name'}}}," +
                "{\$addFields:{__micronaut_nulls_0:{\$cond:[{\$in:[{\$type:'\$__micronaut_sort_0'},['missing','null']]},1,0]}}}," +
                "{\$sort:{__micronaut_nulls_0:1,__micronaut_sort_0:1}}," +
                "{\$unset:['__micronaut_sort_0','__micronaut_nulls_0']}]"
    }

    void "test ordering by an unsupported expression is rejected"() {
        when:
        criteriaQuery.orderBy(criteriaBuilder.asc(criteriaBuilder.count(entityRoot)))
        query

        then:
        def e = thrown(UnsupportedOperationException)
        e.message.contains("is not supported by Micronaut Data MongoDB")
    }

    private String getQuery() {
        criteriaQuery.build(AnnotationMetadata.EMPTY_METADATA, new MongoQueryBuilder()).getQuery()
    }
}
