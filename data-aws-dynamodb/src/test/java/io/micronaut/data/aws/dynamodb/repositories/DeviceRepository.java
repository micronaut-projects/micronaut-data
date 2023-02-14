package io.micronaut.data.aws.dynamodb.repositories;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.aws.dynamodb.annotation.DynamoDbRepository;
import io.micronaut.data.aws.dynamodb.annotation.UseIndex;
import io.micronaut.data.aws.dynamodb.entities.Device;
import io.micronaut.data.aws.dynamodb.entities.DeviceId;
import io.micronaut.data.repository.GenericRepository;

import java.util.List;
import java.util.Optional;

@DynamoDbRepository
public interface DeviceRepository extends GenericRepository<Device, DeviceId> {

    Optional<Device> findById(@NonNull @Id DeviceId id);

    @UseIndex("CountryRegionIndex")
    List<Device> findByCountryAndRegion(String country, String region);

    List<Device> findByVendorId(Long vendorId);
}
