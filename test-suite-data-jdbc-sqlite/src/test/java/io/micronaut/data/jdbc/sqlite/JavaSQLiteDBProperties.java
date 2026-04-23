package io.micronaut.data.jdbc.sqlite;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface JavaSQLiteDBProperties {
    String name() default "mydb";

    String packages() default "io.micronaut.data.tck.entities,io.micronaut.data.tck.jdbc.entities,io.micronaut.data.jdbc.sqlite";

    String schemaGenerate() default "CREATE_DROP";

    String dialect() default "ANSI";

    String dbType() default "sqlite";

    String driverClassName() default "org.sqlite.JDBC";

    String url() default "jdbc:sqlite:file:%s?mode=memory&cache=shared&foreign_keys=ON&busy_timeout=5000";

    String username() default "";

    String password() default "";
}
