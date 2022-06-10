package io.micronaut.data.hibernate.reactive;

import io.micronaut.test.support.TestPropertyProvider;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

public interface PostgresHibernateReactiveProperties extends TestPropertyProvider {

    PostgreSQLContainer<?> CONTAINER = new PostgreSQLContainer<>(DockerImageName.parse("postgres:10"));

    @Override
    default Map<String, String> getProperties() {
        CONTAINER.start();
        return Map.of(
                "jpa.default.properties.hibernate.hbm2ddl.auto", "create-drop",
                "jpa.default.reactive", "true",
                "jpa.default.properties.hibernate.connection.url", CONTAINER.getJdbcUrl(),
                "jpa.default.properties.hibernate.connection.username", CONTAINER.getUsername(),
                "jpa.default.properties.hibernate.connection.password", CONTAINER.getPassword()
        );
    }
}
