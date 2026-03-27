package io.micronaut.data.nitrite.mongoport.repositories;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.mongoport.entities.NitriteCustomer;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.PageableRepository;

@NitriteRepository
public interface NitriteCustomerRepository extends CrudRepository<NitriteCustomer, String>, PageableRepository<NitriteCustomer, String> {
}
