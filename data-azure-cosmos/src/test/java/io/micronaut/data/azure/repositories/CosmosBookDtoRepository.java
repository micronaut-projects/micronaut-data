package io.micronaut.data.azure.repositories;

import io.micronaut.data.azure.entities.CosmosBook;
import io.micronaut.data.cosmos.annotation.CosmosRepository;
import io.micronaut.data.document.tck.entities.BookDto;
import io.micronaut.data.repository.GenericRepository;

import java.util.Optional;

@CosmosRepository
public abstract class CosmosBookDtoRepository implements GenericRepository<CosmosBook, String> {

    public abstract Optional<BookDto> findByTitleAndTotalPages(String title, int totalPages);

    public abstract Optional<BookDto> findById(String id);
}
