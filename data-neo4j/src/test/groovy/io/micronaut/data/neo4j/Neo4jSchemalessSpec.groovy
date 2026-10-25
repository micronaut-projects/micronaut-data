package io.micronaut.data.neo4j

import io.micronaut.data.neo4j.features.Pet
import io.micronaut.data.neo4j.features.PetRepository
import io.micronaut.data.neo4j.features.Tag
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

    void "a schemaless property cannot take the name of a declared one"() {
        given:
        def pet = new Pet("Dino")
        pet.set(name, "x")

        when:
        petRepository.save(pet)

        then:
        def e = thrown(io.micronaut.data.exceptions.DataAccessException)
        e.message.contains("Dynamic attribute [$name]")

        where:
        name << ['name', 'species']
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

    void "dynamic relationships"() {
        given:
        def dino = new Pet("Dino")
        def tag = new Tag("prehistoric")
        dino.related = [PLAYS_WITH: [new Pet("Hoppy"), new Pet("Baby Puss")], TAGGED: [tag]]

        when:
        petRepository.save(dino)
        def found = petRepository.findById("Dino").get()

        then:
        found.related.keySet() == ["PLAYS_WITH", "TAGGED"] as Set
        found.related.PLAYS_WITH*.name.sort() == ["Baby Puss", "Hoppy"]
        found.related.TAGGED.size() == 1
        found.related.TAGGED[0] instanceof Tag
        found.related.TAGGED[0].name == "prehistoric"
        petRepository.findById("Hoppy").get().related.isEmpty()

        when: "saving again does not duplicate relationships"
        petRepository.update(found)

        then:
        petRepository.findById("Dino").get().related.PLAYS_WITH.size() == 2
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
