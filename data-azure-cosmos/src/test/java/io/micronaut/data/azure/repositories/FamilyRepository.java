package io.micronaut.data.azure.repositories;

import com.azure.cosmos.models.PartitionKey;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.azure.entities.Address;
import io.micronaut.data.azure.entities.Family;
import io.micronaut.data.cosmos.annotation.CosmosRepository;
import io.micronaut.data.repository.PageableRepository;

import java.util.Optional;

@CosmosRepository
public abstract class FamilyRepository implements PageableRepository<Family, String> {

    @Nullable
    public abstract Optional<Family> findById(String id);

    public abstract void updateRegistered(@Id String id, boolean registered);

    public abstract void updateRegistered(@Id String id, boolean registered, PartitionKey partitionKey);

    public abstract void updateAddress(@Parameter("id") @Id String id, @NonNull @Parameter("address") Address address);

    // Raw query for Cosmos update is not supported and calling this method will throw an error.
    @Query("UPDATE family SET lastName=@p1 WHERE id=@p2")
    public abstract void updateLastName(String id, String lastName);

    public abstract void deleteByLastName(String lastName, PartitionKey partitionKey);

    public abstract void deleteById(String id, PartitionKey partitionKey);

    // Raw query not supported for delete so this would throw an error
    @Query("DELETE FROM family WHERE registered=@p1")
    public abstract void deleteByRegistered(boolean registered);
}
