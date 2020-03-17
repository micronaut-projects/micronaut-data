package io.micronaut.data.hibernate

import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.hibernate.entities.Pet
import io.micronaut.data.repository.CrudRepository

import java.util.stream.Stream

@Repository
interface PetRepository extends CrudRepository<Pet, UUID> {

    List<Pet.PetType> listDistinctType()

    @Query(value = "SELECT DISTINCT type FROM pet", nativeQuery = true)
    Stream<Pet.PetType> findPetTypesNative()

    @Query(value = "SELECT DISTINCT name FROM pet", nativeQuery = true)
    Stream<String> findPetNamesNative()

    @Query(value = "SELECT DISTINCT p.type FROM Pet p")
    Stream<Pet.PetType> findPetTypesHQL()
}

