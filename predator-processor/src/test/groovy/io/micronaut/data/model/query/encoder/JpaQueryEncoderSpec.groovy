package io.micronaut.data.model.query.encoder

import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.query.Query
import io.micronaut.data.model.query.QueryParameter
import io.micronaut.data.model.query.encoder.entities.Person
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import spock.lang.Specification
import spock.lang.Unroll

class JpaQueryEncoderSpec extends Specification {


    @Unroll
    void "test encode query #method - comparison methods"() {
        given:
        PersistentEntity entity = new RuntimePersistentEntity(type)
        Query q = Query.from(entity)
        q."$method"(property, QueryParameter.of('test'))

        QueryEncoder encoder = new JpaQueryEncoder()
        EncodedQuery encodedQuery = encoder.encodeQuery(q)


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
        QueryEncoder encoder = new JpaQueryEncoder()
        EncodedQuery encodedQuery = encoder.encodeQuery(q)


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

        QueryEncoder encoder = new JpaQueryEncoder()
        EncodedQuery encodedQuery = encoder.encodeQuery(q)


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

        QueryEncoder encoder = new JpaQueryEncoder()
        EncodedQuery encodedQuery = encoder.encodeQuery(q)


        expect:
        encodedQuery != null
        encodedQuery.query ==
                "SELECT $entity.decapitalizedName FROM $entity.name AS $entity.decapitalizedName WHERE (($entity.decapitalizedName.$property >= :p1 AND $entity.decapitalizedName.$property <= :p2))"
        encodedQuery.parameters == ['p1': 'from', 'p2': 'to']

        where:
        type   | method   | property
        Person | 'between' | 'name'
    }

    @Unroll
    void "test encode query #method - simple"() {
        given:
        PersistentEntity entity = new RuntimePersistentEntity(type)
        Query q = Query.from(entity)
        q."$method"(property)

        QueryEncoder encoder = new JpaQueryEncoder()
        EncodedQuery encodedQuery = encoder.encodeQuery(q)


        expect:
        encodedQuery != null
        encodedQuery.query ==
                "SELECT $entity.decapitalizedName FROM $entity.name AS $entity.decapitalizedName WHERE ($entity.decapitalizedName.$property $operator )"
        encodedQuery.parameters.isEmpty()

        where:
        type   | method       | property | operator
        Person | 'isNull'     | 'name'   | 'IS NULL'
        Person | 'isNotNull'  | 'name'   | 'IS NOT NULL'
        Person | 'isEmpty'    | 'name'   | 'IS EMPTY'
        Person | 'isNotEmpty' | 'name'   | 'IS NOT EMPTY'
    }
}
