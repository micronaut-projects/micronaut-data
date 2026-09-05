package io.micronaut.data.nitrite.tck;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.document.tck.entities.Document;
import io.micronaut.data.document.tck.repositories.DocumentRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import java.util.Optional;

@NitriteRepository
public interface NitriteDocumentRepository extends DocumentRepository {

  @Query("{\"title\": :title}")
  Optional<Document> findByTitle(String title);

  @Query("{\"id\": :id, \"$set\": {\"title\": :title}}")
  void updateTitle(@Id String id, String title);

  @Query(value = "{}", countQuery = "{}")
  Page<Document> findAll(Pageable pageable);
}
