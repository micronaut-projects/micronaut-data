package io.micronaut.data.tck.repositories;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.Face;

public interface FaceRepository extends CrudRepository<Face, Long> {

    @Join("nose")
    Face queryById(Long id);
}
