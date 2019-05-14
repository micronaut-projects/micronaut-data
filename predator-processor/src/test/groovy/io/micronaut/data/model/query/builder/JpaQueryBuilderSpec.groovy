/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.model.query.builder

import io.micronaut.core.naming.NameUtils
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.query.Query
import io.micronaut.data.model.query.QueryParameter
import io.micronaut.data.model.query.Sort
import io.micronaut.data.model.entities.Person
import io.micronaut.data.model.query.builder.jpa.JpaQueryBuilder
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import spock.lang.Specification
import spock.lang.Unroll

class JpaQueryBuilderSpec extends Specification {

    @Unroll
    void "test encode query #statement - order by"() {
        given:
        PersistentEntity entity = new RuntimePersistentEntity(type)
        Query q = Query.from(entity)
        q.sort Sort.of(props.collect() { Sort.Order."$direction"(it)})

        QueryBuilder encoder = new JpaQueryBuilder()
        PreparedQuery encodedQuery = encoder.buildQuery(q)


        expect:
        encodedQuery != null
        encodedQuery.query ==
                "SELECT $entity.decapitalizedName FROM $entity.name AS ${entity.decapitalizedName} ORDER BY ${statement}"

        where:
        type   | direction | props           | statement
        Person | 'asc'     | ["name"]        | 'person.name ASC'
        Person | 'asc'     | ["name", "age"] | 'person.name ASC,person.age ASC'
        Person | 'desc'    | ["name"]        | 'person.name DESC'
        Person | 'desc'    | ["name", "age"] | 'person.name DESC,person.age DESC'
    }

    @Unroll
    void "test encode query #method - comparison methods"() {
        given:
        PersistentEntity entity = new RuntimePersistentEntity(type)
        Query q = Query.from(entity)
        q."$method"(property, QueryParameter.of('test'))

        QueryBuilder encoder = new JpaQueryBuilder()
        PreparedQuery encodedQuery = encoder.buildQuery(q)


        expect:
        encodedQuery != null
        encodedQuery.query ==
                "SELECT $entity.decapitalizedName FROM $entity.name AS $entity.decapitalizedName WHERE ($entity.decapitalizedName.$property $operator :p1)"
        encodedQuery.parameters == ['p1': 'test']

        where:
        type   | method | property | operator
        Person | 'eq'   | 'name'   | '='
        Person | 'gt'   | 'name'   | '>'
        Person | 'lt'   | 'name'   | '<'
        Person | 'ge'   | 'name'   | '>='
        Person | 'le'   | 'name'   | '<='
        Person | 'like' | 'name'   | 'like'
        Person | 'ne'   | 'name'   | '!='
    }

    @Unroll
    void "test encode query #method - property projections"() {
        given:
        PersistentEntity entity = new RuntimePersistentEntity(type)
        Query q = Query.from(entity)
        q."$method"(property, QueryParameter.of('test'))
        q.projections()."$projection"(property)
        QueryBuilder encoder = new JpaQueryBuilder()
        PreparedQuery encodedQuery = encoder.buildQuery(q)


        expect:
        encodedQuery != null
        encodedQuery.query ==
                "SELECT ${projection.toUpperCase()}(${entity.decapitalizedName}.$property) FROM $entity.name AS $entity.decapitalizedName WHERE ($entity.decapitalizedName.$property $operator :p1)"
        encodedQuery.parameters == ['p1': 'test']

        where:
        type   | method | property | operator | projection
        Person | 'eq'   | 'name'   | '='      | 'max'
        Person | 'gt'   | 'name'   | '>'      | 'min'
        Person | 'lt'   | 'name'   | '<'      | 'sum'
        Person | 'ge'   | 'name'   | '>='     | 'avg'
        Person | 'le'   | 'name'   | '<='     | 'distinct'
    }

    @Unroll
    void "test encode query #method - inList"() {
        given:
        PersistentEntity entity = new RuntimePersistentEntity(type)
        Query q = Query.from(entity)
        q."$method"(property, QueryParameter.of('test'))

        QueryBuilder encoder = new JpaQueryBuilder()
        PreparedQuery encodedQuery = encoder.buildQuery(q)


        expect:
        encodedQuery != null
        encodedQuery.query ==
                "SELECT $entity.decapitalizedName FROM $entity.name AS $entity.decapitalizedName WHERE ($entity.decapitalizedName.$property IN (:p1))"
        encodedQuery.parameters == ['p1': 'test']

        where:
        type   | method   | property
        Person | 'inList' | 'name'
    }


    @Unroll
    void "test encode query #method - between"() {
        given:
        PersistentEntity entity = new RuntimePersistentEntity(type)
        Query q = Query.from(entity)
        q.between(property, QueryParameter.of("from"), QueryParameter.of("to"))

        QueryBuilder encoder = new JpaQueryBuilder()
        PreparedQuery encodedQuery = encoder.buildQuery(q)


        expect:
        encodedQuery != null
        encodedQuery.query ==
                "SELECT $entity.decapitalizedName FROM $entity.name AS $entity.decapitalizedName WHERE (($entity.decapitalizedName.$property >= :p1 AND $entity.decapitalizedName.$property <= :p2))"
        encodedQuery.parameters == ['p1': 'from', 'p2': 'to']

        where:
        type   | method    | property
        Person | 'between' | 'name'
    }

    @Unroll
    void "test encode query #method - simple"() {
        given:
        PersistentEntity entity = new RuntimePersistentEntity(type)
        Query q = Query.from(entity)
        q."$method"(property)

        QueryBuilder encoder = new JpaQueryBuilder()
        PreparedQuery encodedQuery = encoder.buildQuery(q)


        expect:
        encodedQuery != null
        encodedQuery.query ==
                "SELECT $entity.decapitalizedName FROM $entity.name AS $entity.decapitalizedName WHERE ($entity.decapitalizedName.$property $operator )"
        encodedQuery.parameters.isEmpty()

        where:
        type   | method       | property | operator
        Person | 'isNull'     | 'name'   | 'IS NULL'
        Person | 'isNotNull'  | 'name'   | 'IS NOT NULL'
        Person | 'isEmpty'    | 'name'   | "IS NULL OR ${NameUtils.decapitalize(Person.simpleName)}.$property = \'\'"
        Person | 'isNotEmpty' | 'name'   | "IS NOT NULL AND ${NameUtils.decapitalize(Person.simpleName)}.$property <> \'\'"
    }
}
