package io.micronaut.data.tck.repositories;

import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.Meal;

import java.util.UUID;

public interface MealRepository extends CrudRepository<Meal, UUID> {
}
