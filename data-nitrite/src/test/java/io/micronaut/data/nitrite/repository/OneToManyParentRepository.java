package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.OneToManyParent;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@NitriteRepository
public interface OneToManyParentRepository extends CrudRepository<OneToManyParent, String> {
    List<OneToManyParent> findByChildrenName(String name);
}
