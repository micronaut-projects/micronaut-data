package example.controllers;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import example.domain.NameDTO;
import example.domain.Pet;
import example.repositories.PetRepository;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

@Controller("/pets")
class PetController {

    private final PetRepository petRepository;

    PetController(PetRepository petRepository) {
        this.petRepository = petRepository;
    }

    @Get("/{?number,size}")
    List<NameDTO> all(@Nullable Integer number, @Nullable Integer size) {
        if (number != null && size != null) {
            return petRepository.list(Pageable.from(number, Math.min(size, 100)));
        } else {
            return petRepository.list(Pageable.from(0, 10));
        }        
    }

    @Get("/{name}")
    Optional<Pet> byName(String name) {
        return petRepository.findByName(name);
    }

}