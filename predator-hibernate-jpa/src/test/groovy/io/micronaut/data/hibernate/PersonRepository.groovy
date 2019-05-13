package io.micronaut.data.hibernate

import io.micronaut.data.annotation.Repository
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Slice

@Repository
interface PersonRepository {

    Page<Person> findByNameLike(String name, Pageable pageable)

    Page<Person> list(Pageable pageable)

    Slice<Person> find(Pageable pageable)

    Slice<Person> queryByNameLike(String name, Pageable pageable)

    Optional<Person> findOptionalByName(String name)

    Person findByName(String name)

    List<Person> findAllByName(String name)

    List<Person> findAllByNameLike(String name, Pageable pageable)
}