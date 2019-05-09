package io.micronaut.data.hibernate;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.CrudRepository;

import javax.transaction.Transactional;
import java.util.List;
import java.util.stream.Stream;

@Repository
@Transactional
public interface BookRepository extends CrudRepository<Book, Long> {

    List<Book> findByAuthorName(String name);

    List<Book> findTop3OrderByTitle();

    Stream<Book> findTop3ByAuthorNameOrderByTitle(String name);
}
