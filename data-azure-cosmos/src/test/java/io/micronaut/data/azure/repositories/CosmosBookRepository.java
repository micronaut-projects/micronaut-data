package io.micronaut.data.azure.repositories;

import com.azure.cosmos.models.PartitionKey;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.azure.entities.CosmosBook;
import io.micronaut.data.cosmos.annotation.CosmosRepository;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Slice;
import io.micronaut.data.repository.PageableRepository;

import java.util.List;
import java.util.Optional;

@CosmosRepository
public abstract class CosmosBookRepository implements PageableRepository<CosmosBook, String> {

    @Nullable
    public abstract CosmosBook queryById(String id);

    public abstract Optional<CosmosBook> queryById(String id, PartitionKey partitionKey);

    public abstract CosmosBook searchByTitle(String title);

    public abstract Slice<CosmosBook> list(Pageable pageable);

    public abstract List<CosmosBook> findByTotalPagesGreaterThan(int totalPages, Pageable pageable);
}
