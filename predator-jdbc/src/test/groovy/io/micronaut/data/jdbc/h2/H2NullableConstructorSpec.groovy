package io.micronaut.data.jdbc.h2

import io.micronaut.context.annotation.Property
import io.micronaut.data.tck.entities.Plant
import io.micronaut.data.tck.repositories.PlantRepository
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = "datasources.default.schema-generate", value = "CREATE_DROP")
@Property(name = "datasources.default.dialect", value = "H2")
class H2NullableConstructorSpec extends Specification {

    @Inject
    @Shared
    PlantRepository plantRepository

    void "test save and retrieve nullable association"() {
        when:
        def plant = plantRepository.save(new Plant("Orange", null))

        then:
        plant.id

        when:
        plant = plantRepository.findById(plant.id)

        then:
        plant.id
        plant.name == "Orange"
        plant.nursery == null
    }
}
