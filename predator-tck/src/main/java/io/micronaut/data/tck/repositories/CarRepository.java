package io.micronaut.data.tck.repositories;

import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.Car;

public interface CarRepository extends CrudRepository<Car, Long> {
}
