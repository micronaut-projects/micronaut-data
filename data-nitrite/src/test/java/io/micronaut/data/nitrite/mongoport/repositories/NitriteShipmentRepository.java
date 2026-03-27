package io.micronaut.data.nitrite.mongoport.repositories;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.mongoport.entities.NitriteShipment;
import io.micronaut.data.nitrite.mongoport.entities.NitriteShipmentId;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.PageableRepository;

@NitriteRepository
public interface NitriteShipmentRepository extends CrudRepository<NitriteShipment, NitriteShipmentId>, PageableRepository<NitriteShipment, NitriteShipmentId> {
}
