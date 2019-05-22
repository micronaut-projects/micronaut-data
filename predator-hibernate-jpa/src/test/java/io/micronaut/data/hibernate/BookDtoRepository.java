package io.micronaut.data.hibernate;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.repository.GenericRepository;

import java.util.List;
import java.util.stream.Stream;

@Repository
public interface BookDtoRepository extends GenericRepository<Book, Long> {
    List<BookDto> findByTitleLike(String title);

    BookDto findOneByTitle(String title);

    Page<BookDto> searchByTitleLike(String title, Pageable pageable);

    Stream<BookDto> findStream(String title);
}
