package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.CompositeIdCollectionChild;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@NitriteRepository
public interface CompositeIdCollectionChildRepository extends CrudRepository<CompositeIdCollectionChild, String> {

    List<CompositeIdCollectionChild> findByParentName(String name);
}
