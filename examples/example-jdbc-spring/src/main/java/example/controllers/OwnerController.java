package example.controllers;

import java.util.List;
import java.util.Optional;

import javax.validation.constraints.NotBlank;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import example.domain.Owner;
import example.repositories.OwnerRepository;

@RestController
@RequestMapping("/owners")
class OwnerController {

    private final OwnerRepository ownerRepository;

    OwnerController(OwnerRepository ownerRepository) {
        this.ownerRepository = ownerRepository;
    }

    @GetMapping("/")
    List<Owner> all() {
        return ownerRepository.findAll();
    }

    @GetMapping("/{name}")
    Optional<Owner> byName(@PathVariable("name") @NotBlank String name) {
        return ownerRepository.findByName(name);
    }
}