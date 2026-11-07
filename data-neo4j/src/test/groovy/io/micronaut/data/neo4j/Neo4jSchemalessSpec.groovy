package io.micronaut.data.neo4j

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.model.runtime.PagedQuery
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

    void "a schemaless property cannot take the name that locks a versioned node"() {
        given:
        def pet = new Pet("Dino")
        pet.set("__lock", "x")

        when:
        petRepository.save(pet)

        then:
        def e = thrown(io.micronaut.data.exceptions.DataAccessException)
        e.message.contains("Dynamic attribute [__lock]")
        e.message.contains("reserved")
        petRepository.count() == 0
    }

    void "a schemaless property or label that cannot be quoted is rejected"() {
        given:
        def pet = new Pet("Dino")
        pet.set("x` = 1 WITH n DETACH DELETE n //", true)

        when:
        petRepository.save(pet)

        then:
        def e = thrown(io.micronaut.data.exceptions.DataAccessException)
        e.message.contains("cannot be stored")
        petRepository.count() == 0

        when:
        def cat = new Pet("Tom")
        cat.labels = ["Cat`) DETACH DELETE (n"] as Set
        petRepository.save(cat)

        then:
        def labelError = thrown(io.micronaut.data.exceptions.DataAccessException)
        labelError.message.contains("Label [")

        and: "the node was not written"
        !petRepository.findById("Tom").present
    }

    void "taking a label out of the collection leaves it on the node"() {
        given:
        def cat = new Pet("Tom")
        cat.labels = ["Cat"] as Set
        petRepository.save(cat)

        when:
        def found = petRepository.findById("Tom").get()
        found.labels = [] as Set
        petRepository.update(found)

        then:
        petRepository.findById("Tom").get().labels == ["Cat"] as Set
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

    void "a dynamic relationship to a node of an unmapped label survives load, modify and save"() {
        given:
        operations.executeQuery("CREATE (:Pet {name: 'Dino'})-[:OWNS]->(:Unmapped {name: 'bone'})", [:])

        when:
        def dino = petRepository.findById("Dino").get()

        then:
        dino.related.OWNS == [[name: "bone"]]

        when:
        dino.set('legs', 4)
        def saved = petRepository.update(dino)

        then:
        saved.related.OWNS == [[name: "bone"]]
        petRepository.findById("Dino").get().get('legs') == 4L
        operations.executeQuery("MATCH (:Pet {name: 'Dino'})-[:OWNS]->(u:Unmapped) RETURN u.name", [:], String) == ["bone"]
    }

    void "a dynamic relationship to something that is not a node is rejected"() {
        given:
        def dino = new Pet("Dino")
        dino.related = [OWNS: ["bone"]]

        when:
        petRepository.save(dino)

        then:
        thrown(RuntimeException)
    }

    void "find all with a paged query collects dynamic relationships"() {
        given:
        def dino = new Pet("Dino")
        dino.related = [PLAYS_WITH: [new Pet("Hoppy")]]
        petRepository.save(dino)

        when:
        def pets = operations.findAll(pagedPets()).toList()

        then:
        pets*.name == ["Dino", "Hoppy"]
        pets[0].related.PLAYS_WITH*.name == ["Hoppy"]
    }

    private static PagedQuery<Pet> pagedPets() {
        return new PagedQuery<Pet>() {
            @Override
            Class<Pet> getRootEntity() {
                return Pet
            }

            @Override
            Pageable getPageable() {
                return Pageable.from(0, 10, Sort.of(Sort.Order.asc("name")))
            }

            @Override
            String getName() {
                return "findAll"
            }

            @Override
            AnnotationMetadata getAnnotationMetadata() {
                return AnnotationMetadata.EMPTY_METADATA
            }
        }
    }

    void "a dynamic relationship type that cannot be quoted is rejected"() {
        given:
        def dino = new Pet("Dino")
        dino.related = ["PLAYS_WITH`]->() WITH n DETACH DELETE n //": [new Pet("Hoppy")]]

        when:
        petRepository.save(dino)

        then:
        def e = thrown(io.micronaut.data.exceptions.DataAccessException)
        e.message.contains("Relationship type [")
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
