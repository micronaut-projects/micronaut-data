package io.micronaut.data.nitrite

import io.micronaut.data.nitrite.model.DualIdTestEntity
import io.micronaut.data.nitrite.repository.DualIdTestRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class DualIdDeleteByIdSpec extends Specification {

    @Inject
    DualIdTestRepository repo

    def setup() {
        repo.deleteAll()
    }

    void "test deleteById with dual @Id annotations does not wipe collection"() {
        given:
            def keep = repo.save(new DualIdTestEntity(UUID.randomUUID(), "keep"))

        when:
            repo.deleteById(UUID.randomUUID())

        then:
            repo.findById(keep.id).isPresent()
            repo.count() == 1
    }
}
