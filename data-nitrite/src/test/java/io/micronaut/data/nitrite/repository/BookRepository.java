package io.micronaut.data.nitrite.repository;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.Book;
import io.micronaut.data.repository.CrudRepository;
import java.util.Optional;

/**
 * Simple repository backing the Book examples.
 */
@NitriteRepository
public interface BookRepository extends CrudRepository<Book, String> {

  /**
   * Finds a book by exact title.
   *
   * @param title title value
   * @return optional book
   */
  Optional<Book> findByTitle(String title);

  /**
   * Updates the book title identified by {@code id}.
   *
   * @param id document id
   * @param title new title
   */
  void update(@Id String id, String title);
}
