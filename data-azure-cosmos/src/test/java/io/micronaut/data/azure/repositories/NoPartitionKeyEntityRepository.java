package io.micronaut.data.azure.repositories;

import com.azure.cosmos.models.PartitionKey;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.azure.entities.nopartitionkey.NoPartitionKeyEntity;
import io.micronaut.data.cosmos.annotation.CosmosRepository;
import io.micronaut.data.repository.CrudRepository;

@CosmosRepository
public interface NoPartitionKeyEntityRepository extends CrudRepository<NoPartitionKeyEntity, String> {

    void updateGrade(@Id String id, int grade);

    // This query should not update records because it is using partition key but
    // partition key is not defined on container/entity
    void updateGrade(@Id String id, int grade, PartitionKey partitionKey);

    void deleteByName(String name);

    // This query should not delete records because it is using partition key but
    // partition key is not defined on container/entity
    void deleteByGrade(int grade, PartitionKey partitionKey);
}
