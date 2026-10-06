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

import io.micronaut.data.model.query.builder.jpa.JpaQueryBuilder
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.runtime.criteria.RuntimeCriteriaBuilder
import io.micronaut.data.tck.entities.Contact
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class SqlArithmeticSelectionSpec extends Specification {

    @Shared
    RuntimeCriteriaBuilder builder = new RuntimeCriteriaBuilder()

    @Unroll
    void "test nested arithmetic in a selection keeps its precedence: #expected"() {
        given:
        def query = builder.createQuery(Integer)
        def root = query.from(Contact)
        query.select(selection.call(builder, root.get("age")))

        when:
        def result = query.build(new SqlQueryBuilder(Dialect.H2))

        then:
        result.query == "SELECT $expected FROM `TBL_CONTACT` c"

        where:
        expected                        | selection
        'c.`age` + ?'                   | { cb, age -> cb.sum(age, 1) }
        '(c.`age` + ?) * ?'             | { cb, age -> cb.prod(cb.sum(age, 1), 2) }
        '? * (c.`age` + ?)'             | { cb, age -> cb.prod(2, cb.sum(age, 1)) }
        'c.`age` - (c.`age` - ?)'       | { cb, age -> cb.diff(age, cb.diff(age, 1)) }
        '(c.`age` - ?) / (c.`age` + ?)' | { cb, age -> cb.quot(cb.diff(age, 1), cb.sum(age, 1)) }
        '(c.`age` * ?) + (c.`age` / ?)' | { cb, age -> cb.sum(cb.prod(age, 2), cb.quot(age, 3)) }
        'MAX(c.`age` + ?)'              | { cb, age -> cb.max(cb.sum(age, 1)) }
    }

    void "test nested arithmetic in a selection matches the predicate rendering"() {
        given:
        def query = builder.createQuery(Integer)
        def root = query.from(Contact)
        query.select(builder.prod(builder.sum(root.get("age"), 100), 2))
        query.where(builder.equal(builder.prod(builder.sum(root.get("age"), 100), 2), 123))

        when:
        def result = query.build(new SqlQueryBuilder(Dialect.POSTGRES))

        then:
        result.query == 'SELECT (c."age" + ?) * ? FROM "TBL_CONTACT" c WHERE (((c."age" + ?) * ?) = ?)'
    }

    void "test nested arithmetic in a JPA selection keeps its precedence"() {
        given:
        def query = builder.createQuery(Integer)
        def root = query.from(Contact)
        query.select(builder.prod(builder.sum(root.get("age"), 1), 2))

        when:
        def result = query.build(new JpaQueryBuilder())

        then:
        result.query == 'SELECT (c.age + :p1) * :p2 FROM io.micronaut.data.tck.entities.Contact AS c'
    }
}
