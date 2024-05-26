package io.micronaut.data.hibernate.querygroupby;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.hibernate.entities.MicronautProject;
import io.micronaut.data.repository.CrudRepository;
@Repository
public interface MicronautProjectRepository extends CrudRepository<MicronautProject, Long> {
}
