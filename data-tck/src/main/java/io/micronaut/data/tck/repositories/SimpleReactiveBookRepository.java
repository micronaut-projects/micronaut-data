package io.micronaut.data.tck.repositories;

import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Book;
import org.reactivestreams.Publisher;

public interface SimpleReactiveBookRepository extends GenericRepository<Book, Long> {

    Publisher<? extends Book> save(Book book);

    Publisher<Long> deleteAll();

    Publisher<Long> count();

}
