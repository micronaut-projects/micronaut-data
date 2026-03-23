package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.CriteriaBook;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;

@NitriteRepository
public interface CriteriaBookRepository extends CrudRepository<CriteriaBook, String>, JpaSpecificationExecutor<CriteriaBook> {
}
