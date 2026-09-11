package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.R1Book;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

@NitriteRepository
public interface R1BookRepository
    extends CrudRepository<R1Book, String>, JpaSpecificationExecutor<R1Book> {
    Optional<R1Book> findByTitle(String title);
    long countDistinctTitle();
    // G3: FK match — author is stored as an FK in the book document
    List<R1Book> findByAuthorId(String authorId);
    // G5: forward single-hop MANY_TO_ONE traversal
    List<R1Book> findByAuthorName(String name);
}
