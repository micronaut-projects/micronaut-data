package io.micronaut.data.age.features;

import io.micronaut.data.age.annotation.AgeRepository;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.repository.CrudRepository;

import java.util.Optional;

@AgeRepository
public interface EnsembleRepository extends CrudRepository<Ensemble, String> {
    @Join("seats")
    Optional<Ensemble> findByName(String name);
}
