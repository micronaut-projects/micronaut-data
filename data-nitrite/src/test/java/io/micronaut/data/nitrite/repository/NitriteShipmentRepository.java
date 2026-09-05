package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.NitriteShipment;
import io.micronaut.data.nitrite.model.NitriteShipmentId;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.PageableRepository;

@NitriteRepository
public interface NitriteShipmentRepository extends CrudRepository<NitriteShipment, NitriteShipmentId>, PageableRepository<NitriteShipment, NitriteShipmentId> {
}
