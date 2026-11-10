package io.micronaut.data.graph.model.query.builder

import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.graph.model.query.builder.entities.Employee
import io.micronaut.data.graph.model.query.builder.entities.Movie
import io.micronaut.data.graph.model.query.builder.entities.Person
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import spock.lang.Specification

/**
 * Checks the vertex table view of a node with a single relationship and the check of node writes.
 */
class OracleVertexWriteSpec extends Specification {

    void "the vertex table of a node leaves out its single relationships"() {
        given:
        RuntimePersistentEntity<Employee> entity = entity(Employee)
        PersistentEntity table = OracleVertexTableEntity.of(entity)

        expect:
        table != entity
        table.persistentPropertyNames == ['name', 'movies']
        table.name == entity.name
        table.persistedName == entity.persistedName
        table.aliasName == entity.aliasName
        table.annotationMetadata.is(entity.annotationMetadata)
        !table.hasCompositeIdentity()
        table.hasIdentity()
        table.identity.is(entity.identity)
        table.hasVersion()
        table.version.is(entity.version)
        table.getPropertyByName('name').is(entity.getPropertyByName('name'))
        table.getPropertyByNameIgnoreCase('NAME').is(entity.getPropertyByNameIgnoreCase('NAME'))
        table.isOwningEntity(entity) == entity.isOwningEntity(entity)
        table.parentEntity == entity.parentEntity
        table.namingStrategy.is(entity.namingStrategy)
        table.findNamingStrategy() == entity.findNamingStrategy()
    }

    void "the vertex table delegates the composite identity"() {
        when:
        OracleVertexTableEntity.of(entity(Employee)).compositeIdentity

        then:
        IllegalStateException e = thrown()
        e.message.contains('composite identity')
    }

    void "a node without single relationships is its own vertex table"() {
        given:
        RuntimePersistentEntity<Person> entity = entity(Person)

        expect:
        OracleVertexTableEntity.of(entity).is(entity)
    }

    void "a node write fails when it would lose a relationship"() {
        given:
        RuntimePersistentEntity<Employee> entity = entity(Employee)

        when:
        OracleGraphQueryBuilder.checkVertexWrite(entity, new Employee(manager: new Person()))

        then:
        DataAccessException e = thrown()
        e.message.contains('relationship [manager] set')

        when:
        OracleGraphQueryBuilder.checkVertexWrite(entity, new Employee(movies: [new Movie()]))

        then:
        e = thrown()
        e.message.contains('relationships [movies] not empty')

        when:
        OracleGraphQueryBuilder.checkVertexWrite(entity, new Employee(name: 'Ann', movies: []))

        then:
        noExceptionThrown()
    }

    private static <T> RuntimePersistentEntity<T> entity(Class<T> type) {
        return new RuntimePersistentEntity<T>(type) {
            @Override
            protected RuntimePersistentEntity<Object> getEntity(Class<Object> nestedType) {
                return new RuntimePersistentEntity<Object>(nestedType)
            }
        }
    }
}
