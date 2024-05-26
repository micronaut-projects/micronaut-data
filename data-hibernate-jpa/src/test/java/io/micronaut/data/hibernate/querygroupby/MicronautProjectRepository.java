package io.micronaut.data.hibernate.querygroupby;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.hibernate.entities.MicronautProject;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@Repository
public interface MicronautProjectRepository extends CrudRepository<MicronautProject, Long> {
    @Query("select p from MicronautProject p where p.name=?1 and p.description=?2")
    List<MicronautProject> findWithNameAndDescriptionPositionalBind(String name, String description);
}
