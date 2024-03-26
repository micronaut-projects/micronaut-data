package io.micronaut.data.model.runtime

import io.micronaut.data.annotation.AutoPopulated
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Version
import spock.lang.Specification

class RuntimePersistentEntitySpec extends Specification {

    def "test properties"() {
        given:
            def rtpe = new RuntimePersistentEntity(Test)
        expect:
            rtpe.getPersistentPropertyNames().contains('id')
            rtpe.getIdOrVersionPropertyByName('id') != null
            rtpe.getIdOrVersionPropertyByName('name') == null
            rtpe.getIdOrVersionPropertyByName('version') != null
            rtpe.getIdOrVersionPropertyByName('name') == null
    }

}

@MappedEntity
class Test {
    @Id
    @AutoPopulated
    UUID id

    String name

    @Version
    Long version
}
