package io.micronaut.data.hibernate;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.Shipment;
import io.micronaut.data.tck.entities.ShipmentId;

@Repository
public interface JpaShipmentRepository extends CrudRepository<Shipment, ShipmentId> {
}
