package io.micronaut.data.azure.repositories;

import com.azure.cosmos.models.PartitionKey;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.azure.entities.CosmosBook;
import io.micronaut.data.cosmos.annotation.CosmosRepository;
import io.micronaut.data.repository.PageableRepository;

import java.util.Optional;

@CosmosRepository
public abstract class CosmosBookRepository implements PageableRepository<CosmosBook, String> {

    @Nullable
    public abstract CosmosBook queryById(String id);

    @Nullable
    public abstract Optional<CosmosBook> queryById(String id, PartitionKey partitionKey);

    public abstract CosmosBook searchByTitle(String title);
}
