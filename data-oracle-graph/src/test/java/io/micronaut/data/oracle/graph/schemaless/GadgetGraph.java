package io.micronaut.data.oracle.graph.schemaless;

import io.micronaut.data.oracle.graph.annotation.OracleGraphRepository;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;

import java.util.List;

@OracleGraphRepository(graph = "gadgets")
public interface GadgetGraph extends CrudRepository<Gadget, String>, JpaSpecificationExecutor<Gadget> {

    List<Gadget> findByPriceGreaterThanOrderByName(int price);
}
