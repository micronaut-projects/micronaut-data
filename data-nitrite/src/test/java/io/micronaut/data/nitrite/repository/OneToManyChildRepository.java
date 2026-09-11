package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.OneToManyChild;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@NitriteRepository
public interface OneToManyChildRepository extends CrudRepository<OneToManyChild, String> {
    List<OneToManyChild> findByParentName(String name);
}
