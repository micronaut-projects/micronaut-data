package io.micronaut.data.hibernate

import io.micronaut.data.annotation.Repository

@Repository
interface PersonRepository {

    Person findByName(String name)
}