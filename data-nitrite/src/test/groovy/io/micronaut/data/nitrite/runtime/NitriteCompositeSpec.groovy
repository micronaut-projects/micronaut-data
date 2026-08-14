package io.micronaut.data.nitrite.runtime

import io.micronaut.data.nitrite.model.NitriteProject
import io.micronaut.data.nitrite.model.NitriteProjectId
import io.micronaut.data.nitrite.repository.NitriteProjectRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class NitriteCompositeSpec extends Specification implements NitriteTestPropertyProvider {

    @Inject
    NitriteProjectRepository repository

    def cleanup() {
        repository.deleteAll()
    }

    void "test composite id CRUD"() {
        when:
        NitriteProjectId id = new NitriteProjectId("CODE1", "US")
        repository.save(new NitriteProject(id: id, name: "Project Alpha"))

        NitriteProjectId id2 = new NitriteProjectId("CODE2", "UK")
        repository.save(new NitriteProject(id: id2, name: "Project Beta"))

        def entity = repository.findById(id).orElse(null)

        then:
        repository.count() == 2
        entity != null
        entity.name == "Project Alpha"

        when:"the entity is updated"
        entity.name = 'Project Alpha Updated'
        repository.update(entity)
        entity = repository.findById(id).orElse(null)

        then:"The update completes correctly"
        entity != null
        entity.name == 'Project Alpha Updated'

        when:"The entity is deleted"
        repository.deleteById(id2)

        then:"The delete works"
        repository.count() == 1
    }

    void "composite identity remains usable for lookup after a round trip"() {
        given:
        def id = new NitriteProjectId("CODE3", "DE")
        repository.save(new NitriteProject(id: id, name: "Project Gamma"))

        when:
        def loaded = repository.findById(id).orElse(null)

        then:
        loaded != null
        loaded.id.code == "CODE3"
        loaded.id.country == "DE"
    }
}
