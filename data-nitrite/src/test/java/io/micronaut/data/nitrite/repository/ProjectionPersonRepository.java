package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.CriteriaPerson;
import io.micronaut.data.repository.CrudRepository;
import java.util.List;
import java.util.Optional;

@NitriteRepository
public interface ProjectionPersonRepository extends CrudRepository<CriteriaPerson, String> {

    // Single property projection (findOne)
    Optional<Integer> findAgeByName(String name);

    // Single property projection (findAll)
    List<String> findNameByAgeGreaterThan(int age);
    
    // Explicit projection using conventions
    List<Integer> listAgeByNameLike(String name);

    // Aggregation projections
    long countByNameLike(String name);
    long countDistinctName();
}
