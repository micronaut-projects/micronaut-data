package io.micronaut.data.jdbc.h2

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

@Target([ElementType.ANNOTATION_TYPE, ElementType.TYPE])
@Retention(RetentionPolicy.RUNTIME)
@interface H2DBProperties {
    String name() default "mydb"

    String packages() default "io.micronaut.data.tck.entities,io.micronaut.data.tck.jdbc.entities,io.micronaut.data.jdbc.h2"

    String schemaGenerate() default "CREATE_DROP"

    String dialect() default "H2"

    String driverClassName() default "org.h2.Driver"

    String url() default "jdbc:h2:mem:mydb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE"

    String username() default "sa"

    String password() default ""
}
