package io.micronaut.data.document.mongodb.repositories;

import io.micronaut.data.document.tck.entities.Address;
import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.document.tck.repositories.RestaurantRepository;

import java.util.List;
import java.util.Optional;

@MongoRepository
public interface MongoRestaurantRepository extends RestaurantRepository {

    Address findAddressById(String id);

    Optional<Address> findHqAddressById(String id);

    List<Address> findHqAddressByName(String name);
}
