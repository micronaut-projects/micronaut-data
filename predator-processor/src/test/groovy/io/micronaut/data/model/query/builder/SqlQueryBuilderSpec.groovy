package io.micronaut.data.model.query.builder

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.entities.Person
import io.micronaut.data.model.entities.PersonAssignedId
import io.micronaut.data.model.naming.NamingStrategies
import io.micronaut.data.model.naming.NamingStrategy
import io.micronaut.data.model.query.QueryModel
import io.micronaut.data.model.query.QueryParameter
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import spock.lang.Specification
import spock.lang.Unroll

class SqlQueryBuilderSpec extends Specification {

    void "test encode insert statement"() {
        given:
        PersistentEntity entity = new RuntimePersistentEntity(Person)
        QueryBuilder encoder = new SqlQueryBuilder()
        def result = encoder.buildInsert(AnnotationMetadata.EMPTY_METADATA, entity)

        expect:
        result.query == 'INSERT INTO person (name,age,enabled) VALUES (?,?,?)'
        result.parameters.equals(name:'1', age:'2', enabled:'3')
    }

    void "test encode insert statement - assigned id"() {
        given:
        PersistentEntity entity = new RuntimePersistentEntity(PersonAssignedId)
        QueryBuilder encoder = new SqlQueryBuilder()
        def result = encoder.buildInsert(AnnotationMetadata.EMPTY_METADATA, entity)

        expect:
        result.query == 'INSERT INTO person_assigned_id (name,age,enabled,id) VALUES (?,?,?,?)'
        result.parameters.equals(name:'1', age:'2', enabled:'3', id:'4')
    }

    @Unroll
    void "test encode query #method - comparison methods"() {
        given:
        PersistentEntity entity = new RuntimePersistentEntity(type)
        QueryModel q = QueryModel.from(entity)
        q."$method"(property, QueryParameter.of('test'))

        QueryBuilder encoder = new SqlQueryBuilder()
        QueryResult encodedQuery = encoder.buildQuery(q)
        NamingStrategy namingStrategy = NamingStrategies.UnderScoreSeparatedLowerCase.newInstance()
        def mappedName = namingStrategy.mappedName(property)

        expect:
        encodedQuery != null
        mappedName == 'some_id'
        encodedQuery.query ==
                "SELECT person.id,person.name,person.age,person.some_id,person.enabled FROM person AS person WHERE (person.${ mappedName} $operator ?)"
        encodedQuery.parameters == ['1': 'test']

        where:
        type   | method | property | operator
        Person | 'eq'   | 'someId' | '='
        Person | 'gt'   | 'someId' | '>'
        Person | 'lt'   | 'someId' | '<'
        Person | 'ge'   | 'someId' | '>='
        Person | 'le'   | 'someId' | '<='
        Person | 'like' | 'someId' | 'like'
        Person | 'ne'   | 'someId' | '!='
    }
}
