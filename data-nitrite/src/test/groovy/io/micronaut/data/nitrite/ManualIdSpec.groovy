package io.micronaut.data.nitrite

import io.micronaut.data.nitrite.model.ManualIdEntity
import io.micronaut.data.nitrite.repository.ManualIdRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class ManualIdSpec extends Specification {

    @Inject
    ManualIdRepository repository

    void "test manual UUID preserved"() {
        given:
        UUID manualId = UUID.randomUUID()
        ManualIdEntity entity = new ManualIdEntity(manualId, "Test Entity")

        when:
        repository.save(entity)

        then:
        def found = repository.findById(manualId)
        found.isPresent()
        found.get().id == manualId
        found.get().name == "Test Entity"
    }

    void "test default UUID preserved"() {
        given:
        ManualIdEntity entity = new ManualIdEntity("Default ID Entity")
        UUID defaultId = entity.id

        when:
        repository.save(entity)

        then:
        def found = repository.findById(defaultId)
        found.isPresent()
        found.get().id == defaultId
    }

    void "test update with manual UUID"() {
        given:
        UUID manualId = UUID.randomUUID()
        ManualIdEntity entity = new ManualIdEntity(manualId, "Original Name")
        repository.save(entity)

        when:
        entity.name = "Updated Name"
        repository.update(entity)

        then:
        def found = repository.findById(manualId)
        found.isPresent()
        found.get().id == manualId
        found.get().name == "Updated Name"
    }
}
