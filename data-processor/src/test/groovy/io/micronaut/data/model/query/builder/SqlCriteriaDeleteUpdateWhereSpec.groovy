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
package io.micronaut.data.model.query.builder

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.runtime.criteria.RuntimeCriteriaBuilder
import io.micronaut.data.tck.entities.Contact
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Predicate
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

/**
 * The restriction of a criteria delete or update is normalized like the one of a criteria query, so that
 * empty and single element conjunctions and disjunctions produce valid SQL.
 */
class SqlCriteriaDeleteUpdateWhereSpec extends Specification {

    @Shared
    RuntimeCriteriaBuilder builder = new RuntimeCriteriaBuilder()

    @Shared
    SqlQueryBuilder sqlQueryBuilder = new SqlQueryBuilder(Dialect.H2)

    @Unroll
    void "test criteria query, delete and update with #description restriction"() {
        given:
        def query = builder.createQuery(Contact)
        def queryRoot = query.from(Contact)
        query.where((Expression<Boolean>) restriction.call(builder, queryRoot))
        def delete = builder.createCriteriaDelete(Contact)
        def deleteRoot = delete.from(Contact)
        delete.where((Expression<Boolean>) restriction.call(builder, deleteRoot))
        def update = builder.createCriteriaUpdate(Contact)
        def updateRoot = update.from(Contact)
        update.set("name", builder.parameter(String))
        update.where((Expression<Boolean>) restriction.call(builder, updateRoot))

        expect:
        query.build(sqlQueryBuilder).query.endsWith(" FROM `TBL_CONTACT` c" + queryWhere)
        delete.build(sqlQueryBuilder).query.stripTrailing() == "DELETE  FROM `TBL_CONTACT` $where".stripTrailing()
        update.build(sqlQueryBuilder).query == "UPDATE `TBL_CONTACT` SET `name`=?" + where

        where:
        description             | restriction                                         | queryWhere                    | where
        "a single predicate"    | { cb, root -> cb.ge(root.get("age"), 18) }         | ' WHERE (c.`age` >= ?)'       | ' WHERE (`age` >= ?)'
        "a single element and"  | { cb, root -> cb.and(cb.ge(root.get("age"), 18)) } | ' WHERE (c.`age` >= ?)'       | ' WHERE (`age` >= ?)'
        "a single element or"   | { cb, root -> cb.or(cb.ge(root.get("age"), 18)) }  | ' WHERE (c.`age` >= ?)'       | ' WHERE (`age` >= ?)'
        "an empty and"          | { cb, root -> cb.and() }                           | ''                            | ''
        "an empty or"           | { cb, root -> cb.or() }                            | ' WHERE ? = ?'                | ' WHERE 1 = 2'
    }

    @Unroll
    void "test criteria delete and update with a single #description varargs restriction"() {
        given:
        def delete = builder.createCriteriaDelete(Contact)
        def deleteRoot = delete.from(Contact)
        delete.where([restriction.call(builder, deleteRoot)] as Predicate[])
        def update = builder.createCriteriaUpdate(Contact)
        def updateRoot = update.from(Contact)
        update.set("name", builder.parameter(String))
        update.where([restriction.call(builder, updateRoot)] as Predicate[])

        expect:
        delete.build(sqlQueryBuilder).query.stripTrailing() == "DELETE  FROM `TBL_CONTACT` $where".stripTrailing()
        update.build(sqlQueryBuilder).query == "UPDATE `TBL_CONTACT` SET `name`=?" + where

        where:
        description           | restriction                                         | where
        "element and"         | { cb, root -> cb.and(cb.ge(root.get("age"), 18)) } | ' WHERE (`age` >= ?)'
        "element or"          | { cb, root -> cb.or(cb.ge(root.get("age"), 18)) }  | ' WHERE (`age` >= ?)'
        "empty and"           | { cb, root -> cb.and() }                           | ''
        "empty or"            | { cb, root -> cb.or() }                            | ' WHERE 1 = 2'
    }
}
