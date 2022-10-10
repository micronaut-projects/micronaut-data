package io.micronaut.data.azure.repositories;

import com.azure.cosmos.models.PartitionKey;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.azure.entities.CosmosBook;
import io.micronaut.data.azure.entities.Family;
import io.micronaut.data.cosmos.annotation.CosmosRepository;
import io.micronaut.data.repository.PageableRepository;

import java.util.Optional;

@CosmosRepository
public abstract class FamilyRepository implements PageableRepository<Family, String> {

    @Nullable
    public abstract Optional<Family> findById(String id);

    public abstract void deleteByLastName(String lastName, PartitionKey partitionKey);

    public abstract void deleteById(String id, PartitionKey partitionKey);

    // Raw query not supported for delete so this would throw an error
    @Query("DELETE FROM family WHERE registered=@p1")
    public abstract void deleteByRegistered(boolean registered);
}
