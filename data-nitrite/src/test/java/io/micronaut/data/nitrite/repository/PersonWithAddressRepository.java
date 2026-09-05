package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.PersonWithAddress;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.PageableRepository;
import java.util.List;

/**
 * Repository for testing nested embedded object queries. Tests Gap 2: No embedded document / nested
 * object field.
 */
@NitriteRepository
public interface PersonWithAddressRepository
    extends CrudRepository<PersonWithAddress, String>,
        PageableRepository<PersonWithAddress, String> {
  // Basic queries only - nested property queries not supported by annotation processor
  List<PersonWithAddress> findByName(String name);
}
