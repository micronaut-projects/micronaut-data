package io.micronaut.data.oracle.graph.schemaless;

import io.micronaut.data.oracle.graph.annotation.OracleGraphRepository;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@OracleGraphRepository(graph = "gadgets")
public interface GadgetGraph extends CrudRepository<Gadget, String> {

    List<Gadget> findByPriceGreaterThanOrderByName(int price);
}
