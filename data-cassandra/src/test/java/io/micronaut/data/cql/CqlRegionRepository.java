package io.micronaut.data.cql;

import io.micronaut.data.cql.annotation.CqlRepository;
import io.micronaut.data.tck.repositories.RegionRepository;

@CqlRepository()
public interface CqlRegionRepository extends RegionRepository{

}
