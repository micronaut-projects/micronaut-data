package io.micronaut.data.azure.repositories;

import com.azure.cosmos.models.PartitionKey;
import io.micronaut.data.azure.entities.User;
import io.micronaut.data.cosmos.annotation.CosmosRepository;
import io.micronaut.data.repository.CrudRepository;

import java.util.Optional;

@CosmosRepository
public interface UserRepository extends CrudRepository<User, Long> {

    Optional<User> queryByUserId(Long userId);

    Optional<User> queryByUserId(Long userId, PartitionKey partitionKey);
}
