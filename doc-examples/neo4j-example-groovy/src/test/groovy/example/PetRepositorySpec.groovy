package example

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

@MicronautTest(transactional = false)
class PetRepositorySpec extends AbstractNeo4jSpec {

    @Inject
    PetRepository petRepository

    void cleanup() {
        petRepository.deleteAll()
    }

    void "schemaless properties"() {
        when:
        // tag::schemaless[]
        def dino = new Pet(name: "Dino")
        dino.goesInPatch = true // <1>
        dino['legs'] = 4
        petRepository.save(dino)

        def found = petRepository.findById("Dino").get()
        def goesInPatch = found.goesInPatch // <2>
        def inPatch = petRepository.findByGoesInPatch(true) // <3>
        // end::schemaless[]

        then:
        goesInPatch == true
        found['legs'] == 4
        inPatch*.name == ["Dino"]
    }
}
