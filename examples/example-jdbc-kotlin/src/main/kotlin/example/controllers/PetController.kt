package example.controllers

import java.util.Optional

import example.domain.NameDTO
import example.domain.Pet
import example.repositories.PetRepository
import io.micronaut.data.model.Pageable
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

@Controller("/pets")
open class PetController(private val petRepository: PetRepository) {

    @Get("/")
    fun all(pageable: Pageable): List<NameDTO> {
        return petRepository.list(pageable)
    }

    @Get("/{name}")
    fun byName(name: String): Optional<Pet> {
        return petRepository.findByName(name)
    }
}