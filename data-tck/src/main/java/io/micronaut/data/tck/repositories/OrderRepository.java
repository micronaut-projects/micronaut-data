package io.micronaut.data.tck.repositories;

import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.Order;

public interface OrderRepository extends CrudRepository<Order,Long> {
}
