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

import io.micronaut.data.annotation.Join
import io.micronaut.data.model.Association
import io.micronaut.data.model.DataType
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.Sort
import io.micronaut.data.model.entities.Person
import io.micronaut.data.model.query.QueryModel
import io.micronaut.data.model.query.QueryParameter
import io.micronaut.data.model.query.builder.jpa.JpaQueryBuilder
import io.micronaut.data.model.query.factory.Projections
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.tck.entities.Challenge
import io.micronaut.data.tck.entities.EntityWithIdClass
import io.micronaut.data.tck.entities.Meal
import io.micronaut.data.tck.entities.Shipment
import io.micronaut.data.tck.entities.UuidEntity
import io.micronaut.data.tck.jdbc.entities.UserRole
import spock.lang.Shared
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
        encodedQuery.parameters == ['p1': 'name']

        where:
        type   | method | property | operator
        Person | 'eq'   | 'name'   | '='
        Person | 'gt'   | 'name'   | '>'
        Person | 'lt'   | 'name'   | '<'
        Person | 'ge'   | 'name'   | '>='
        Person | 'le'   | 'name'   | '<='
        Person | 'like' | 'name'   | 'LIKE'
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
        encodedQuery.parameters == ['p1': 'name']

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
        encodedQuery.queryParts ==
                ["SELECT $alias FROM $entity.name AS $alias WHERE ($alias.$property IN (", "))"]
        encodedQuery.parameters == ['p1': 'name']

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
        encodedQuery.parameters == ['p1': 'name', 'p2': 'name']

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
                "SELECT $alias FROM $entity.name AS $alias WHERE ($alias.$property $operator)"
        encodedQuery.parameters.isEmpty()

        where:
        type   | method       | property | operator
        Person | 'isNull'     | 'name'   | 'IS NULL'
        Person | 'isNotNull'  | 'name'   | 'IS NOT NULL'
        Person | 'isEmpty'    | 'name'   | "IS NULL OR person_.$property = \'\'"
        Person | 'isNotEmpty' | 'name'   | "IS NOT NULL AND person_.$property <> \'\'"
    }

    @Unroll
    void "test queries"() {
        when:
            QueryBuilder encoder = new JpaQueryBuilder()
            QueryResult encodedQuery = encoder.buildQuery(queryModel)

        then:
            encodedQuery.query == query

        where:
            queryModel << [
                    QueryModel.from(getRuntimePersistentEntity(Shipment)).idEq(new QueryParameter("xyz")),
                    QueryModel.from(getRuntimePersistentEntity(Shipment)).eq("shipmentId.country", new QueryParameter("xyz")),
                    {
                        def entity = getRuntimePersistentEntity(UserRole)
                        def qm = QueryModel.from(entity)
                        qm.join("role", entity.getPropertyByPath("id.role").get() as Association, Join.Type.DEFAULT, null)
                        qm
                    }.call(),
                    {
                        def entity = getRuntimePersistentEntity(UserRole)
                        def qm = QueryModel.from(entity)
                        qm.join("user", entity.getPropertyByPath("id.user").get() as Association, Join.Type.DEFAULT, null)
                        qm.eq("user", new QueryParameter("xyz"))
                    }.call(),
                    QueryModel.from(getRuntimePersistentEntity(UuidEntity)).idEq(new QueryParameter("xyz")),
                    QueryModel.from(getRuntimePersistentEntity(UserRole)).idEq(new QueryParameter("xyz")),
                    {
                        def entity = getRuntimePersistentEntity(Challenge)
                        def qm = QueryModel.from(entity)
                        qm.join("authentication", null, Join.Type.FETCH, null)
                        qm.join("authentication.device", null, Join.Type.FETCH, null)
                        qm.join("authentication.device.user", null, Join.Type.FETCH, null)
                        qm.idEq(new QueryParameter("xyz"))
                        qm
                    }.call(),
                    {
                        def entity = getRuntimePersistentEntity(UserRole)
                        def qm = QueryModel.from(entity)
                        qm.projections().add(Projections.property("role"))
                        qm.join("role", null, Join.Type.FETCH, null)
                        qm.eq("user", new QueryParameter("xyz"))
                        qm
                    }.call(),
                    {
                        def entity = getRuntimePersistentEntity(Meal)
                        def qm = QueryModel.from(entity)
                        qm.join("foods", null, Join.Type.FETCH, null)
                        qm.idEq(new QueryParameter("xyz"))
                        qm
                    }.call()
            ]
            query << [
                    'SELECT shipment_ FROM io.micronaut.data.tck.entities.Shipment AS shipment_ WHERE (shipment_.shipmentId = :p1)',
                    'SELECT shipment_ FROM io.micronaut.data.tck.entities.Shipment AS shipment_ WHERE (shipment_.shipmentId.country = :p1)',
                    'SELECT userRole_ FROM io.micronaut.data.tck.jdbc.entities.UserRole AS userRole_ JOIN userRole_.role userRole_id_role_',
                    'SELECT userRole_ FROM io.micronaut.data.tck.jdbc.entities.UserRole AS userRole_ JOIN userRole_.user userRole_id_user_ WHERE (userRole_.id.user = :p1)',
                    'SELECT uidx FROM io.micronaut.data.tck.entities.UuidEntity AS uidx WHERE (uidx.uuid = :p1)',
                    'SELECT userRole_ FROM io.micronaut.data.tck.jdbc.entities.UserRole AS userRole_ WHERE (userRole_.id = :p1)',
                    'SELECT challenge_ FROM io.micronaut.data.tck.entities.Challenge AS challenge_ JOIN FETCH challenge_.authentication challenge_authentication_ JOIN FETCH challenge_authentication_.device challenge_authentication_device_ JOIN FETCH challenge_authentication_device_.user challenge_authentication_device_user_ WHERE (challenge_.id = :p1)',
                    'SELECT userRole_id_role_ FROM io.micronaut.data.tck.jdbc.entities.UserRole AS userRole_ JOIN FETCH userRole_.role userRole_id_role_ WHERE (userRole_.id.user = :p1)',
                    'SELECT meal_ FROM io.micronaut.data.tck.entities.Meal AS meal_ JOIN FETCH meal_.foods meal_foods_ WHERE (meal_.mid = :p1 AND (meal_.actual = \'Y\' AND meal_foods_.fresh = \'Y\'))'
            ]
    }

    void "test composite id query"() {
        when:
            QueryBuilder encoder = new JpaQueryBuilder()
            def entity = getRuntimePersistentEntity(EntityWithIdClass)
            def qm = QueryModel.from(entity)
            qm.idEq(new QueryParameter("xyz"))
            def result = encoder.buildQuery(qm)
        then:
            result.query == 'SELECT entityWithIdClass_ FROM io.micronaut.data.tck.entities.EntityWithIdClass AS entityWithIdClass_ WHERE (entityWithIdClass_.id1 = :p1 AND entityWithIdClass_.id2 = :p2)'
            result.parameters == ['p1': 'id1', 'p2': 'id2']
            result.parameterTypes == ['id1': DataType.LONG, 'id2': DataType.LONG]
    }

    void "test composite id delete"() {
        when:
            QueryBuilder encoder = new JpaQueryBuilder()
            def entity = getRuntimePersistentEntity(EntityWithIdClass)
            def qm = QueryModel.from(entity)
            qm.idEq(new QueryParameter("xyz"))
            def result = encoder.buildDelete(qm)
        then:
            result.query == 'DELETE io.micronaut.data.tck.entities.EntityWithIdClass  AS entityWithIdClass_ WHERE (entityWithIdClass_.id1 = :p1 AND entityWithIdClass_.id2 = :p2)'
            result.parameters == ['p1': 'id1', 'p2': 'id2']
            result.parameterTypes == ['id1': DataType.LONG, 'id2': DataType.LONG]
    }

    void "test composite id update"() {
        when:
            QueryBuilder encoder = new JpaQueryBuilder()
            def entity = getRuntimePersistentEntity(EntityWithIdClass)
            def qm = QueryModel.from(entity)
            qm.idEq(new QueryParameter("xyz"))
            def result = encoder.buildUpdate(qm, ['name'])
        then:
            result.query == 'UPDATE io.micronaut.data.tck.entities.EntityWithIdClass entityWithIdClass_ SET entityWithIdClass_.name=:p1 WHERE (entityWithIdClass_.id1 = :p2 AND entityWithIdClass_.id2 = :p3)'
            result.parameters == ['p1': 'name', 'p2': 'id1', 'p3': 'id2']
            result.parameterTypes == ['name': DataType.STRING, 'id1': DataType.LONG, 'id2': DataType.LONG]
    }

    @Shared
    Map<Class, RuntimePersistentEntity> entities = [:]

    // entities have instance compare in some cases
    RuntimePersistentEntity getRuntimePersistentEntity(Class type) {
        RuntimePersistentEntity entity = entities.get(type)
        if (entity == null) {
            entity = new RuntimePersistentEntity(type) {
                @Override
                protected RuntimePersistentEntity getEntity(Class t) {
                    return getRuntimePersistentEntity(t)
                }
            }
            entities.put(type, entity)
        }
        return entity
    }
}
