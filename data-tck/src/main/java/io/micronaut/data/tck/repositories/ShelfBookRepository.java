package io.micronaut.data.tck.repositories;

import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.entities.Shelf;
import io.micronaut.data.tck.entities.ShelfBook;

public interface ShelfBookRepository extends GenericRepository<ShelfBook, Object> {

    ShelfBook save(Shelf shelf, Book book);
}
