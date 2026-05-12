package io.micronaut.data.model.runtime

import io.micronaut.data.annotation.AutoPopulated
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import spock.lang.Specification

class RuntimePersistentEntitySpec extends Specification {

    def "test properties"() {
        given:
            def rtpe = new RuntimePersistentEntity(Test)
        expect:
            rtpe.getPersistentPropertyNames().contains('id')
    }

    void "test get property by path stops at missing property on no-id association"() {
        given:
            def rtpe = new RuntimePersistentEntity(NoIdOwner)

        expect:
            rtpe.getPropertyByPath("child.name").isPresent()
            rtpe.getPropertyByPath("child.missing.name").isEmpty()
    }

}

@MappedEntity
class Test {
    @Id
    @AutoPopulated
    UUID id

    String name
}

@MappedEntity
class NoIdOwner {
    @Id
    Long id

    @Relation(Relation.Kind.MANY_TO_ONE)
    NoIdChild child
}

@MappedEntity
class NoIdChild {
    String name
}
