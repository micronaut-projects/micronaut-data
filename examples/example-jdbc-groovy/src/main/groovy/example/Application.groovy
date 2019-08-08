package example

import example.domain.Owner
import example.domain.Pet
import example.domain.PetType
import example.repositories.OwnerRepository
import example.repositories.PetRepository
import groovy.util.logging.Slf4j
import io.micronaut.context.event.StartupEvent
import io.micronaut.runtime.Micronaut
import groovy.transform.CompileStatic
import io.micronaut.runtime.event.annotation.EventListener

import javax.inject.Singleton

@CompileStatic
@Slf4j
@Singleton
class Application {

    final OwnerRepository ownerRepository
    final PetRepository petRepository

    Application(OwnerRepository ownerRepository, PetRepository petRepository) {
        this.ownerRepository = ownerRepository
        this.petRepository = petRepository
    }

    @EventListener
    void init(StartupEvent event) {
        log.info("Populating data")

        Owner fred = new Owner("Fred")
        fred.setAge(45)
        Owner barney = new Owner("Barney")
        barney.setAge(40)
        ownerRepository.saveAll(Arrays.asList(fred, barney))

        Pet dino = new Pet("Dino", fred)
        Pet bp = new Pet("Baby Puss", fred)
        bp.setType(PetType.CAT)
        Pet hoppy = new Pet("Hoppy", barney)

        petRepository.saveAll(Arrays.asList(dino, bp, hoppy))
    }

    static void main(String[] args) {
        Micronaut.run(Application)
    }
}