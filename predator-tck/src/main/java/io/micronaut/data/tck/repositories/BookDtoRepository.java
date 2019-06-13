package io.micronaut.data.tck.repositories;

import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.entities.BookDto;

import java.util.List;
import java.util.stream.Stream;

public interface BookDtoRepository extends GenericRepository<Book, Long> {
    List<BookDto> findByTitleLike(String title);

    BookDto findOneByTitle(String title);

    Page<BookDto> searchByTitleLike(String title, Pageable pageable);

    Stream<BookDto> findStream(String title);
}
