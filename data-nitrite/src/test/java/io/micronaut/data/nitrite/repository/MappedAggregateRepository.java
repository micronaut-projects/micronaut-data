package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.MappedAggregate;
import io.micronaut.data.repository.CrudRepository;

import java.math.BigDecimal;

@NitriteRepository
public interface MappedAggregateRepository extends CrudRepository<MappedAggregate, String> {

    /**
     * Derived aggregate over a property stored under a custom mapped name.
     *
     * @param name the group name
     * @return the largest total value
     */
    BigDecimal findMaxTotalValueByName(String name);
}
