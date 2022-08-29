package io.micronaut.data.document.mongodb.repositories;

import io.micronaut.data.cosmos.annotation.CosmosRepository;
import io.micronaut.data.document.tck.repositories.RestaurantRepository;

@CosmosRepository
public interface CosmosRestaurantRepository extends RestaurantRepository {
}
