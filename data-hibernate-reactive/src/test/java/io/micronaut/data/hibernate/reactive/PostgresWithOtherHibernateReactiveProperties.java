package io.micronaut.data.hibernate.reactive;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.Map;

public interface PostgresWithOtherHibernateReactiveProperties extends PostgresHibernateReactiveProperties {

    PostgreSQLContainer<?> CONTAINER = new PostgreSQLContainer<>(DockerImageName.parse("postgres:10"));

    @Override
    default Map<String, String> getProperties() {
        CONTAINER.start();
        Map<String, String> map = new HashMap<>(PostgresHibernateReactiveProperties.super.getProperties());
        map.putAll(Map.of(
                "jpa.other.properties.hibernate.hbm2ddl.auto", "create-drop",
                "jpa.other.reactive", "true",
                "jpa.other.properties.hibernate.connection.url", CONTAINER.getJdbcUrl(),
                "jpa.other.properties.hibernate.connection.username", CONTAINER.getUsername(),
                "jpa.other.properties.hibernate.connection.password", CONTAINER.getPassword()
        ));
        return map;
    }
}
