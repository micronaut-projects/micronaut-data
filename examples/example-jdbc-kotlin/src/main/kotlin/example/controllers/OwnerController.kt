package example.controllers

import java.util.Optional

import javax.validation.constraints.NotBlank

import example.domain.Owner
import example.repositories.OwnerRepository
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

@Controller("/owners")
open class OwnerController(private val ownerRepository: OwnerRepository) {

    @Get("/")
    fun all(): List<Owner> {
        return ownerRepository.findAll()
    }

    @Get("/{name}")
    open fun byName(@NotBlank name: String): Optional<Owner> {
        return ownerRepository.findByName(name)
    }
}