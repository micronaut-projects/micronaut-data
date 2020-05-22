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

import io.micronaut.core.naming.NameUtils
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.query.QueryModel
import io.micronaut.data.model.query.QueryParameter
import io.micronaut.data.model.Sort
import io.micronaut.data.model.entities.Person
import io.micronaut.data.model.query.builder.jpa.JpaQueryBuilder
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import spock.lang.Specification
import spock.lang.Unroll

class JpaQueryBuilderSpec extends Specification {

    @Unroll
    void "test encode order by #statement"() {
        given:
        PersistentEntity entity = new RuntimePersistentEntity(type)
        QueryModel q = QueryModel.from(entity)
        q.sort Sort.of(props.collect() { Sort.Order."$direction"(it)})

        QueryBuilder encoder = new JpaQueryBuilder()
        QueryResult encodedQuery = encoder.buildOrderBy(entity, q.getSort())


        expect:
        encodedQuery != null
        encodedQuery.query ==
                " ORDER BY ${statement}"

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
        PersistentEntity entity = new RuntimePersistentEntity(type)
        QueryModel q = QueryModel.from(entity)
        q.sort Sort.of(props.collect() { Sort.Order."$direction"(it)})

        QueryBuilder encoder = new JpaQueryBuilder()
        QueryResult encodedQuery = encoder.buildQuery(q)


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

    @Unroll
    void "test encode query #method - comparison methods"() {
        given:
        PersistentEntity entity = new RuntimePersistentEntity(type)
        QueryModel q = QueryModel.from(entity)
        q."$method"(property, QueryParameter.of('test'))

        QueryBuilder encoder = new JpaQueryBuilder()
        QueryResult encodedQuery = encoder.buildQuery(q)
        def alias = encoder.getAliasName(entity)

        expect:
        encodedQuery != null
        encodedQuery.query ==
                "SELECT ${alias} FROM $entity.name AS ${alias} WHERE ($alias.$property $operator :p1)"
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
        QueryModel q = QueryModel.from(entity)
        q."$method"(property, QueryParameter.of('test'))
        q.projections()."$projection"(property)
        QueryBuilder encoder = new JpaQueryBuilder()
        QueryResult encodedQuery = encoder.buildQuery(q)
        def alias = encoder.getAliasName(entity)

        expect:
        encodedQuery != null
        encodedQuery.query ==
                "SELECT ${projection.toUpperCase()}(${alias}.$property) FROM $entity.name AS $alias WHERE ($alias.$property $operator :p1)"
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
        QueryModel q = QueryModel.from(entity)
        q."$method"(property, QueryParameter.of('test'))

        QueryBuilder encoder = new JpaQueryBuilder()
        QueryResult encodedQuery = encoder.buildQuery(q)
        def alias = encoder.getAliasName(entity)

        expect:
        encodedQuery != null
        encodedQuery.query ==
                "SELECT $alias FROM $entity.name AS $alias WHERE ($alias.$property IN (:p1))"
        encodedQuery.parameters == ['p1': 'test']

        where:
        type   | method   | property
        Person | 'inList' | 'name'
    }


    @Unroll
    void "test encode query #method - between"() {
        given:
        PersistentEntity entity = new RuntimePersistentEntity(type)
        QueryModel q = QueryModel.from(entity)
        q.between(property, QueryParameter.of("from"), QueryParameter.of("to"))

        QueryBuilder encoder = new JpaQueryBuilder()
        QueryResult encodedQuery = encoder.buildQuery(q)
        def alias = encoder.getAliasName(entity)

        expect:
        encodedQuery != null
        encodedQuery.query ==
                "SELECT $alias FROM $entity.name AS $alias WHERE (($alias.$property >= :p1 AND $alias.$property <= :p2))"
        encodedQuery.parameters == ['p1': 'from', 'p2': 'to']

        where:
        type   | method    | property
        Person | 'between' | 'name'
    }

    @Unroll
    void "test encode query #method - simple"() {
        given:
        PersistentEntity entity = new RuntimePersistentEntity(type)
        QueryModel q = QueryModel.from(entity)
        q."$method"(property)

        QueryBuilder encoder = new JpaQueryBuilder()
        QueryResult encodedQuery = encoder.buildQuery(q)
        def alias = encoder.getAliasName(entity)

        expect:
        encodedQuery != null
        encodedQuery.query ==
                "SELECT $alias FROM $entity.name AS $alias WHERE ($alias.$property $operator )"
        encodedQuery.parameters.isEmpty()

        where:
        type   | method       | property | operator
        Person | 'isNull'     | 'name'   | 'IS NULL'
        Person | 'isNotNull'  | 'name'   | 'IS NOT NULL'
        Person | 'isEmpty'    | 'name'   | "IS NULL OR person_.$property = \'\'"
        Person | 'isNotEmpty' | 'name'   | "IS NOT NULL AND person_.$property <> \'\'"
    }
}
