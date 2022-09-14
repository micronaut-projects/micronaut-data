package io.micronaut.data.document.mongodb

import com.fasterxml.jackson.annotation.JsonView
import groovy.transform.EqualsAndHashCode
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Property
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

@Issue("https://github.com/micronaut-projects/micronaut-data/issues/1729")
@MicronautTest
class MongoJsonViewSpec extends Specification implements MongoTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    @Inject
    PlanetRepository planetRepository = applicationContext.getBean(PlanetRepository)

    @Property(name = "micronaut.data.mongodb.ignore-json-views", value = "true")
    void 'test-encode-with-json-view'() {
        given:
            Planet planet = new Planet(name: "Earth")
        when:
            planet = planetRepository.save(planet)
            planet = planetRepository.findById(planet.id).get()
        then:
            planet.id
            planet.name == "Earth"
    }


    @Property(name = "micronaut.data.mongodb.ignore-json-views", value = "false")
    void 'test-encode-with-json-view-default'() {
        given:
            Planet planet = new Planet(name: "Mars")
        when:
            planet = planetRepository.save(planet)
             planet = planetRepository.findById(planet.id).get()
        then:
            planet.id
            planet.name == null
    }
}

@EqualsAndHashCode(includes = "id")
@MappedEntity("planets")
class Planet {
    @Id
    @GeneratedValue
    String id

    @JsonView(Views.Public.class)
    String name

}

@MongoRepository
interface PlanetRepository extends CrudRepository<Planet, String> {
}

class Views {
    static class Public {
    }
}
