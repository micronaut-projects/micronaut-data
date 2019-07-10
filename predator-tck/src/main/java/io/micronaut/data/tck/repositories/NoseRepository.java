package io.micronaut.data.tck.repositories;

import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.Nose;

public interface NoseRepository extends CrudRepository<Nose, Long> {
}
