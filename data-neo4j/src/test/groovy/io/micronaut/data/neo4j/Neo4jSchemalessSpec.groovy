package io.micronaut.data.neo4j

import io.micronaut.data.neo4j.features.Pet
import io.micronaut.data.neo4j.features.PetRepository
import io.micronaut.data.neo4j.operations.Neo4jRepositoryOperations
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class Neo4jSchemalessSpec extends Specification implements TestPropertyProvider {

    @Inject
    PetRepository petRepository

    @Inject
    Neo4jRepositoryOperations operations

    @Override
    Map<String, String> getProperties() {
        return ['neo4j.uri': EmbeddedNeo4j.boltUri()]
    }

    void setup() {
        operations.executeQuery("MATCH (n) DETACH DELETE n", [:])
    }

    void "store and read schemaless properties"() {
        given:
        def pet = new Pet("Dino")
        pet.species = "Dinosaur"
        pet.set("goesInPatch", true)
        pet.set('legs', 4)
        pet.set('favouriteFood', "Bronto burgers")

        when:
        petRepository.save(pet)
        def found = petRepository.findById("Dino").get()

        then:
        found.species == "Dinosaur"
        found.get("goesInPatch") == true
        found.get('legs') == 4L
        found.get('favouriteFood') == "Bronto burgers"
        found.attributes.keySet() == ["goesInPatch", "legs", "favouriteFood"] as Set
        !found.attributes.containsKey("species")
    }

    void "query schemaless properties with explicit queries"() {
        given:
        def dino = new Pet("Dino")
        dino.set('goesInPatch', true)
        def hoppy = new Pet("Hoppy")
        hoppy.set('goesInPatch', false)
        petRepository.saveAll([dino, hoppy])

        expect:
        petRepository.findByGoesInPatch(true)*.name == ["Dino"]
    }

    void "remove a schemaless property by setting it to null"() {
        given:
        def pet = new Pet("Dino")
        pet.set('nickname', "Little Dino")
        petRepository.save(pet)

        when:
        def found = petRepository.findById("Dino").get()
        found.set('nickname', null)
        petRepository.update(found)

        then:
        !petRepository.findById("Dino").get().attributes.containsKey("nickname")
    }

    void "dynamic labels"() {
        given:
        def cat = new Pet("Tom")
        cat.labels = ["Cat", "Cartoon"] as Set
        petRepository.save(cat)
        petRepository.save(new Pet("Spike"))

        when:
        def cats = petRepository.findCats()

        then:
        cats*.name == ["Tom"]
        cats[0].labels == ["Cat", "Cartoon"] as Set
        petRepository.findById("Spike").get().labels.isEmpty()
    }
}
