package io.micronaut.data.hibernate;

import io.micronaut.context.annotation.Property;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = "jpa.default.properties.hibernate.hbm2ddl.auto", value = "create-drop")
@Property(name = "datasources.default.dialect", value = "H2")
// This properties can be eliminated after TestResources bug is fixed
@Property(name = "datasources.default.driverClassName", value = "org.h2.Driver")
@Property(name = "datasources.default.url", value = "jdbc:h2:mem:mydb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE")
@Property(name = "datasources.default.username", value = "")
@Property(name = "datasources.default.password", value = "")
public @interface H2DBProperties {
}
