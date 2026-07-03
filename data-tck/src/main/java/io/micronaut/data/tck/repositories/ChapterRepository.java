package io.micronaut.data.tck.repositories;

import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;
import io.micronaut.data.tck.entities.Chapter;

public interface ChapterRepository extends CrudRepository<Chapter, Long>, JpaSpecificationExecutor<Chapter> {
}
