package io.micronaut.data.model.query.encoder

import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.query.encoder.entities.Person
import spock.lang.Specification

class RuntimePersistentEntitySpec extends Specification {

    void "test runtime entity"() {
        given:
        PersistentEntity entity = PersistentEntity.of(Person)

        expect:
        entity.name == Person.name
        entity.identity
        entity.identity.name == 'id'
        entity.persistentProperties
        entity.getPropertyByName("name")
        !entity.getPropertyByName("name").isReadOnly()
        entity.getPropertyByName("someId").isReadOnly()
    }
}
