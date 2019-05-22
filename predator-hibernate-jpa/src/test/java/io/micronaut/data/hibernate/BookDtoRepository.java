package io.micronaut.data.hibernate;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.GenericRepository;

import java.util.List;

@Repository
public interface BookDtoRepository extends GenericRepository<Book, Long> {
    List<BookDto> findByTitleLike(String title);
}
