package io.micronaut.data.nitrite.repository

import io.micronaut.data.nitrite.model.Widget
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

/**
 * Tests for UUID as the primary key type.
 */
@MicronautTest(transactional = false)
class WidgetRepositorySpec extends Specification {

    @Inject
    WidgetRepository widgetRepository

    def setup() {
        widgetRepository.deleteAll()
    }

    void "test UUID @Id is generated on save"() {
        when:
        def saved = widgetRepository.save(new Widget("Cog"))

        then:
        saved.id != null
        saved.id instanceof UUID
    }

    void "test findById with UUID @Id"() {
        given:
        def saved = widgetRepository.save(new Widget("Sprocket"))

        when:
        def found = widgetRepository.findById(saved.id)

        then:
        found.present
        found.get().id == saved.id
        found.get().name == "Sprocket"
    }

    void "test UUID round-trips correctly"() {
        given:
        def saved = widgetRepository.save(new Widget("Bolt"))
        def originalId = saved.id

        when:
        def found = widgetRepository.findById(originalId).get()

        then:
        found.id == originalId
        found.id instanceof UUID
    }

    void "test delete by UUID @Id"() {
        given:
        def saved = widgetRepository.save(new Widget("Nut"))

        when:
        widgetRepository.deleteById(saved.id)

        then:
        !widgetRepository.findById(saved.id).present
    }

    void "test findByName with UUID-keyed entity"() {
        given:
        widgetRepository.save(new Widget("Gear"))
        widgetRepository.save(new Widget("Gear"))
        widgetRepository.save(new Widget("Pin"))

        when:
        def results = widgetRepository.findByName("Gear")

        then:
        results.size() == 2
        results.every { it.name == "Gear" }
    }
}
