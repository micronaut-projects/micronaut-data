package io.micronaut.data.cql;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.cql.annotation.CqlRepository;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.Sale;

import java.util.Map;

@CqlRepository()
public interface CqlSaleRepository extends CrudRepository<Sale, Long> {

    void updateData(@Id Long id, Map<String, String> data);
}
