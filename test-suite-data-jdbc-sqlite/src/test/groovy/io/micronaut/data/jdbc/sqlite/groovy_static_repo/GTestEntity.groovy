package io.micronaut.data.jdbc.sqlite.groovy_static_repo

import groovy.transform.CompileStatic
import io.micronaut.data.annotation.AutoPopulated
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

@MappedEntity
@CompileStatic
class GTestEntity {

    @Id
    @AutoPopulated
    UUID id

    String name
}