package io.micronaut.data.hibernate

import io.micronaut.data.annotation.Repository
import io.micronaut.data.model.Pageable

@Repository
interface PersonRepository {

    Optional<Person> findOptionalByName(String name)

    Person findByName(String name)

    List<Person> findAllByName(String name)

    List<Person> findAllByNameLike(String name, Pageable pageable)
}