package io.micronaut.data.tck.repositories;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Shelf;

import java.util.Optional;

public interface ShelfRepository extends GenericRepository<Shelf, Long> {
    Shelf save(String shelfName);

    Shelf save(Shelf shelf);

    @Join(value = "books", type = Join.Type.LEFT_FETCH, alias = "b_")
    @Join(value = "books.pages", type = Join.Type.LEFT_FETCH, alias = "p_")
    Optional<Shelf> findById(Long id);
}
