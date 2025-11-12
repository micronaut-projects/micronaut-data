/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.Sort
import io.micronaut.data.model.entities.Person
import io.micronaut.data.model.query.builder.jpa.JpaQueryBuilder
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.runtime.criteria.RuntimeCriteriaBuilder
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class JpaQueryBuilderSpec extends Specification {

    @Shared
    RuntimeCriteriaBuilder builder = new RuntimeCriteriaBuilder()

    @Shared
    JpaQueryBuilder jpaQueryBuilder = new JpaQueryBuilder()

    @Unroll
    void "test encode order by #statement"() {
        given:
        PersistentEntity entity = new RuntimePersistentEntity(type)
        Sort sort = Sort.of(props.collect() { Sort.Order."$direction"(it)})

        String query = jpaQueryBuilder.buildOrderBy("", entity, AnnotationMetadata.EMPTY_METADATA, sort, false, null)

        expect:
        query == " ORDER BY ${statement}"

        where:
        type   | direction | props           | statement
        Person | 'asc'     | ["name"]        | 'person_.name ASC'
        Person | 'asc'     | ["name", "age"] | 'person_.name ASC,person_.age ASC'
        Person | 'desc'    | ["name"]        | 'person_.name DESC'
        Person | 'desc'    | ["name", "age"] | 'person_.name DESC,person_.age DESC'
    }

    @Unroll
    void "test encode query #statement - order by"() {
        given:
        def query = builder.createQuery()
        def root = query.from(type)
        def entity = root.persistentEntity
        props.forEach { prop ->

        }
        query.orderBy(props.collect {builder.sort(root.get(it), direction == 'asc', false) })
        QueryResult encodedQuery = query.build(jpaQueryBuilder)

        expect:
        encodedQuery != null
        encodedQuery.query ==
                "SELECT ${entity.decapitalizedName}_ FROM $entity.name AS ${entity.decapitalizedName}_ ORDER BY ${statement}"

        where:
        type   | direction | props           | statement
        Person | 'asc'     | ["name"]        | 'person_.name ASC'
        Person | 'asc'     | ["name", "age"] | 'person_.name ASC,person_.age ASC'
        Person | 'desc'    | ["name"]        | 'person_.name DESC'
        Person | 'desc'    | ["name", "age"] | 'person_.name DESC,person_.age DESC'
    }

}
