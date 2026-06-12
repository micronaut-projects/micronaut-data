package io.micronaut.data.nitrite

import io.micronaut.data.nitrite.model.IntegerIdEntity
import io.micronaut.data.nitrite.model.Widget
import io.micronaut.data.nitrite.repository.IntegerIdEntityRepository
import io.micronaut.data.nitrite.repository.WidgetRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class NitriteGeneratedIdSpec extends Specification {

    @Inject
    WidgetRepository widgetRepository

    @Inject
    IntegerIdEntityRepository integerIdEntityRepository

    def "test UUID generated id"() {
        given:
            def widget = new Widget(name: "Test UUID Widget")
            
        when:
            def saved = widgetRepository.save(widget)
            
        then:
            saved.id != null
            saved.id instanceof UUID
            
        when:
            def found = widgetRepository.findById(saved.id).orElse(null)
            
        then:
            found != null
            found.name == "Test UUID Widget"
    }

    def "test Integer generated id"() {
        given:
            def entity = new IntegerIdEntity(name: "Test Integer Entity")
            
        when:
            def saved = integerIdEntityRepository.save(entity)
            
        then:
            saved.id != null
            saved.id instanceof Integer
            
        when:
            def found = integerIdEntityRepository.findById(saved.id).orElse(null)
            
        then:
            found != null
            found.name == "Test Integer Entity"
    }
}
