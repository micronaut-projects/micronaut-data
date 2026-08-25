package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.R1Review;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@NitriteRepository
public interface R1ReviewRepository extends CrudRepository<R1Review, String> {
    // G4: multi-hop MANY_TO_ONE traversal (review -> book -> author)
    List<R1Review> findByBookAuthorName(String authorName);
}
