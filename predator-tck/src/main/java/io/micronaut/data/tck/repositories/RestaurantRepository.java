package io.micronaut.data.tck.repositories;

import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.Restaurant;

public interface RestaurantRepository extends CrudRepository<Restaurant, Long> {
}
