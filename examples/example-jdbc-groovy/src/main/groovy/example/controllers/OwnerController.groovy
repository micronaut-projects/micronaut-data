package example.controllers

import example.domain.Owner
import example.repositories.OwnerRepository
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

import javax.validation.constraints.NotBlank

@Controller("/owners")
class OwnerController {

    private final OwnerRepository ownerRepository

    OwnerController(OwnerRepository ownerRepository) {
        this.ownerRepository = ownerRepository
    }

    @Get("/")
    List<Owner> all() {
        return ownerRepository.findAll()
    }

    @Get("/{name}")
    Optional<Owner> byName(@NotBlank String name) {
        return ownerRepository.findByName(name)
    }
}