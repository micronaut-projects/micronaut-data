package io.micronaut.data.jdbc.h2

import io.micronaut.context.annotation.Property

import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

@Retention(RetentionPolicy.RUNTIME)
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = "datasources.default.schema-generate", value = "CREATE_DROP")
@Property(name = "datasources.default.dialect", value = "H2")
@Property(name = "datasources.default.packages", value = "io.micronaut.data.tck.entities,io.micronaut.data.tck.jdbc.entities,io.micronaut.data.jdbc.h2")
@interface H2DBProperties {
}
