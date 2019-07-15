package example

import example.domain.Owner
import example.domain.Pet
import example.domain.Pet.PetType
import example.repositories.OwnerRepository
import example.repositories.PetRepository
import io.micronaut.context.event.StartupEvent
import io.micronaut.core.annotation.TypeHint
import io.micronaut.runtime.Micronaut
import io.micronaut.runtime.event.annotation.EventListener
import org.slf4j.LoggerFactory
import javax.inject.Singleton

fun main(args: Array<String>) {
    Micronaut.run(*args)
}

@Singleton
@TypeHint(typeNames = ["org.h2.Driver", "org.h2.mvstore.db.MVTableEngine"])
class Application(
        private val ownerRepository: OwnerRepository,
        private val petRepository: PetRepository) {

    @EventListener
    fun init(event: StartupEvent) {
        if (LOG.isInfoEnabled) {
            LOG.info("Populating data")
        }

        val fred = Owner(0, "Fred", 45)
        val barney = Owner(0, "Barney",40)
        ownerRepository.saveAll(listOf(fred, barney))

        val dino = Pet(null, "Dino", fred)
        val bp = Pet(null, "Baby Puss", fred, PetType.CAT)
        val hoppy = Pet(null, "Hoppy", barney)

        petRepository.saveAll(listOf(dino, bp, hoppy))
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(Application::class.java)
    }
}