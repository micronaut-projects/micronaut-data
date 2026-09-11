package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.CriteriaAuthor;
import io.micronaut.data.repository.CrudRepository;

@NitriteRepository
public interface CriteriaAuthorRepository extends CrudRepository<CriteriaAuthor, String> {
}
