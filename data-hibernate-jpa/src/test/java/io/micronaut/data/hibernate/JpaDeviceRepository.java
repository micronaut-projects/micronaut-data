package io.micronaut.data.hibernate;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.tck.repositories.DeviceRepository;

@Repository
public interface JpaDeviceRepository extends DeviceRepository {
}
