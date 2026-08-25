package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.NitriteItemGroup;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.PageableRepository;

@NitriteRepository
public interface NitriteItemGroupRepository extends CrudRepository<NitriteItemGroup, Long>, PageableRepository<NitriteItemGroup, Long> {
}
