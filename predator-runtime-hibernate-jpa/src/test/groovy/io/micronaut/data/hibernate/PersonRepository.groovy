package io.micronaut.data.hibernate

import io.micronaut.data.annotation.Repository

@Repository
interface PersonRepository {

    Optional<Person> findOptionalByName(String name)

    Person findByName(String name)

    List<Person> findAllByName(String name)
}