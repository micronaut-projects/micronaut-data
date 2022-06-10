package example;

import io.micronaut.test.support.TestPropertyProvider;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

public interface PostgresHibernateSyncAndReactiveProperties extends TestPropertyProvider {

    PostgreSQLContainer<?> CONTAINER = new PostgreSQLContainer<>(DockerImageName.parse("postgres:10"));

    @Override
    default Map<String, String> getProperties() {
        CONTAINER.start();
        return Map.of(
            "jpa.sync.properties.hibernate.hbm2ddl.auto", "create-drop",
            "datasources.sync.url", CONTAINER.getJdbcUrl(),
            "datasources.sync.username", CONTAINER.getUsername(),
            "datasources.sync.password", CONTAINER.getPassword(),
            "jpa.reactive.properties.hibernate.hbm2ddl.auto", "none",
            "jpa.reactive.reactive", "true",
            "jpa.reactive.properties.hibernate.connection.url", CONTAINER.getJdbcUrl(),
            "jpa.reactive.properties.hibernate.connection.username", CONTAINER.getUsername(),
            "jpa.reactive.properties.hibernate.connection.password", CONTAINER.getPassword()
        );
    }
}
