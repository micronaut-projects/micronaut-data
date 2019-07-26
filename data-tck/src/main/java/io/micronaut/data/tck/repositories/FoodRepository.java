package io.micronaut.data.tck.repositories;

import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.Food;

import java.util.UUID;

public interface FoodRepository extends CrudRepository<Food, UUID> {
}
