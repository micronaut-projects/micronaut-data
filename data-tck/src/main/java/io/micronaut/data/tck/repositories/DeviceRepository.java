package io.micronaut.data.tck.repositories;

import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;
import io.micronaut.data.tck.entities.Device;

public interface DeviceRepository extends CrudRepository<Device, Long>, JpaSpecificationExecutor<Device> {
    class Specification {
    }
}
