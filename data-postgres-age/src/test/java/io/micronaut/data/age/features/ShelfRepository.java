package io.micronaut.data.age.features;

import io.micronaut.data.age.annotation.AgeRepository;
import io.micronaut.data.repository.CrudRepository;

@AgeRepository
public interface ShelfRepository extends CrudRepository<Shelf, Long> {
}
