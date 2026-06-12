package io.micronaut.data.nitrite.runtime;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.repository.CrudRepository;

@NitriteRepository
public interface MyTestRepo extends CrudRepository<MyTestEntity, Long> {
}
