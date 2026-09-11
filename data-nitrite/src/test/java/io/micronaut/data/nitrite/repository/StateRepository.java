package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.State;
import io.micronaut.data.repository.CrudRepository;
import java.util.List;

@NitriteRepository
public interface StateRepository extends CrudRepository<State, String> {
    State findByCitiesName(String name);
    List<State> findAllByCitiesName(String name);
}
