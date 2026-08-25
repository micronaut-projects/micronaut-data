package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.OneToManyParent;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;

import java.util.List;

@NitriteRepository
public interface OneToManyParentRepository extends CrudRepository<OneToManyParent, String>, JpaSpecificationExecutor<OneToManyParent> {
    List<OneToManyParent> findByChildrenName(String name);
}
