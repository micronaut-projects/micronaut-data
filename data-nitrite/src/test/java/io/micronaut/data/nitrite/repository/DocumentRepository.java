package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.Document;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.PageableRepository;
import java.util.List;

/**
 * Repository helper for document-related tests.
 */
@NitriteRepository
public interface DocumentRepository
    extends CrudRepository<Document, String>, PageableRepository<Document, String> {
  /**
   * Finds documents matching exactly the supplied title.
   *
   * @param title document title
   * @return matching documents
   */
  List<Document> findByTitle(String title);

  /**
   * Finds documents whose title contains the supplied keyword.
   *
   * @param keyword substring
   * @return matching documents
   */
  List<Document> findByTitleContaining(String keyword);

  /**
   * Finds documents without a title value.
   *
   * @return matching documents
   */
  List<Document> findByTitleIsNull();

  /**
   * Finds documents that declare a title.
   *
   * @return matching documents
   */
  List<Document> findByTitleIsNotNull();

  /**
   * Finds documents with an explicitly empty title.
   *
   * @return matching documents
   */
  List<Document> findByTitleIsEmpty();

  /**
   * Finds documents with a non-empty title string.
   *
   * @return matching documents
   */
  List<Document> findByTitleIsNotEmpty();
}
