package io.micronaut.data.tck.repositories;

import io.micronaut.data.tck.entities.Book;

public interface SimpleBookRepository {

    Book save(Book book);

    void deleteAll();

    long count();

}
