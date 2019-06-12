package io.micronaut.data.jdbc

import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.repository.CrudRepository

@JdbcRepository
interface BasicTypeRepository extends CrudRepository<BasicTypesJava, Long> {

}