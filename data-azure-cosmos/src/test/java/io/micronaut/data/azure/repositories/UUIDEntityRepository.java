package io.micronaut.data.azure.repositories;

import com.azure.cosmos.models.PartitionKey;
import io.micronaut.data.azure.entities.UUIDEntity;
import io.micronaut.data.cosmos.annotation.CosmosRepository;
import io.micronaut.data.repository.CrudRepository;

import java.util.Optional;
import java.util.UUID;

@CosmosRepository
public interface UUIDEntityRepository extends CrudRepository<UUIDEntity, UUID> {

    Optional<UUIDEntity> queryByNumber(UUID uuid);

    Optional<UUIDEntity> queryByNumber(UUID uuid, PartitionKey partitionKey);
}
