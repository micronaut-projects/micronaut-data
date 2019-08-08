package example

import example.domain.Pet
import example.repositories.PetRepository
import io.micronaut.context.BeanContext
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest
class PetRepositorySpec extends Specification {


    @Inject
    PetRepository petRepository


    @Inject
    BeanContext beanContext


    void 'test retrieve pet and owner'() {
        given:
        Pet dino = petRepository.findByName("Dino").orElse(null)

        expect:
        dino != null
        dino.name == 'Dino'
        dino.owner.name == 'Fred'
    }

}
