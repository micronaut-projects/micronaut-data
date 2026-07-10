package io.micronaut.data.nitrite.runtime

import io.micronaut.context.ApplicationContext
import io.micronaut.data.nitrite.model.NitriteComplexEntity
import io.micronaut.data.nitrite.model.NitriteComplexValue
import io.micronaut.data.nitrite.repository.NitriteComplexEntityRepository
import io.micronaut.data.nitrite.mongoport.NitriteTestPropertyProvider
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest
class NitriteRuntimeSpec extends Specification implements NitriteTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    @Inject
    NitriteComplexEntityRepository complexEntityRepository = applicationContext.getBean(NitriteComplexEntityRepository)

    def cleanup() {
        complexEntityRepository.deleteAll()
    }

    void 'test save complex entity with nested value'() {
        when:
            NitriteComplexEntity entity = new NitriteComplexEntity(
                name: "Test Entity",
                value: new NitriteComplexValue("key1", "data1")
            )
            complexEntityRepository.save(entity)
            NitriteComplexEntity retrieved = complexEntityRepository.findById(entity.id).get()

        then:
            retrieved.id
            retrieved.name == "Test Entity"
            retrieved.value.key == "key1"
            retrieved.value.data == "data1"
    }

    void 'test save complex entity with multiple values'() {
        when:
            NitriteComplexEntity entity = new NitriteComplexEntity(
                name: "Multi Value Entity",
                values: [
                    new NitriteComplexValue("k1", "d1"),
                    new NitriteComplexValue("k2", "d2"),
                    new NitriteComplexValue("k3", "d3")
                ]
            )
            complexEntityRepository.save(entity)
            NitriteComplexEntity retrieved = complexEntityRepository.findById(entity.id).get()

        then:
            retrieved.id
            retrieved.name == "Multi Value Entity"
            retrieved.values.size() == 3
            retrieved.values[0].key == "k1"
            retrieved.values[1].key == "k2"
            retrieved.values[2].key == "k3"
    }

    void 'test update complex entity'() {
        when:
            NitriteComplexEntity entity = new NitriteComplexEntity(
                name: "Original",
                value: new NitriteComplexValue("original", "data")
            )
            complexEntityRepository.save(entity)
            
            entity.name = "Updated"
            entity.value = new NitriteComplexValue("updated", "newdata")
            complexEntityRepository.update(entity)
            
            NitriteComplexEntity retrieved = complexEntityRepository.findById(entity.id).get()

        then:
            retrieved.name == "Updated"
            retrieved.value.key == "updated"
            retrieved.value.data == "newdata"
    }
}
