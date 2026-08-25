package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.MappedIdParent;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@NitriteRepository
public interface MappedIdParentRepository extends CrudRepository<MappedIdParent, String> {

    /**
     * Nested reverse-association query: matches parents by a property of their children.
     *
     * @param name the child name
     * @return matching parents
     */
    List<MappedIdParent> findByChildrenName(String name);
}
