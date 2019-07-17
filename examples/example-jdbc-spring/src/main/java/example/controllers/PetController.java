package example.controllers;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import example.domain.NameDTO;
import example.domain.Pet;
import example.repositories.PetRepository;


@RestController
@RequestMapping("/pets")
class PetController {

    private final PetRepository petRepository;

    PetController(PetRepository petRepository) {
        this.petRepository = petRepository;
    }

    @GetMapping("/")
    List<NameDTO> all(Pageable pageable) {
        return petRepository.list(pageable);
    }

    @GetMapping("/{name}")
    Optional<Pet> byName(String name) {
        return petRepository.findByName(name);
    }

}