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
import io.micronaut.data.model.query.builder.sql.SqlDialectOptions
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.runtime.criteria.RuntimeCriteriaBuilder
import io.micronaut.data.tck.entities.Contact
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class SqlCastDialectVersionSpec extends Specification {

    @Shared
    RuntimeCriteriaBuilder builder = new RuntimeCriteriaBuilder()

    @Unroll
    void "test CAST renders the same type in the selection and the predicate for Oracle #version"() {
        given:
        def query = builder.createQuery(Boolean)
        def root = query.from(Contact)
        query.select(root.get("age").cast(Boolean))
        query.where(builder.equal(root.get("age").cast(Boolean), builder.parameter(Boolean)))

        when:
        def result = query.build(new SqlQueryBuilder(Dialect.ORACLE, version))

        then:
        result.query == "SELECT CAST(c.\"AGE\" AS $type) FROM \"TBL_CONTACT\" c WHERE (CAST(c.\"AGE\" AS $type) = ?)"

        where:
        version                                  | type
        null                                     | 'NUMBER(1)'
        SqlDialectOptions.ORACLE_23_1_0_VERSION  | 'BOOLEAN'
    }

    void "test CAST in a delete predicate honours the dialect version"() {
        given:
        def delete = builder.createCriteriaDelete(Contact)
        def root = delete.from(Contact)
        delete.where(builder.equal(root.get("age").cast(Boolean), builder.parameter(Boolean)))

        when:
        def result = delete.build(new SqlQueryBuilder(Dialect.ORACLE, SqlDialectOptions.ORACLE_23_1_0_VERSION))

        then:
        result.query == 'DELETE  FROM "TBL_CONTACT"  WHERE (CAST("AGE" AS BOOLEAN) = ?)'
    }
}
