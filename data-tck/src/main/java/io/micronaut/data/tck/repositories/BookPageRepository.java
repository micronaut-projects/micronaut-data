package io.micronaut.data.tck.repositories;

import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.entities.BookPage;
import io.micronaut.data.tck.entities.Page;

public interface BookPageRepository extends GenericRepository<BookPage, Object> {

    BookPage save(Book book, Page page);
}
