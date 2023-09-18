package io.micronaut.data.hibernate.nativepostgresql;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest(startApplication = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Property(name = "spec.name", value = "nativepostgresql")
@Property(name = "jpa.default.properties.hibernate.hbm2ddl.auto", value = "none")
@Property(name = "datasources.default.db-type", value = "postgres")
@Property(name = "datasources.default.dialect", value = "POSTGRES")
@Property(name = "jpa.default.entity-scan.packages", value = "io.micronaut.data.hibernate.nativepostgresql")
@Property(name = "datasources.default.driver-class-name", value = "org.postgresql.Driver")
class ProductRepositoryTest implements TestPropertyProvider {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            "postgres:15.2-alpine"
    ).withCopyFileToContainer(MountableFile.forClasspathResource("sql/init-db.sql"), "/docker-entrypoint-initdb.d/init-db.sql");

    @Override
    public @NonNull Map<String, String> getProperties() { // <4>
        if (!postgres.isRunning()) {
            postgres.start();
        }
        return Map.of("datasources.default.driver-class-name", "org.postgresql.Driver",
                "datasources.default.url", postgres.getJdbcUrl(),
                "datasources.default.username", postgres.getUsername(),
                "datasources.default.password", postgres.getPassword());
    }

    @Inject
    Connection connection;

    @Inject
    ResourceLoader resourceLoader;

    @Inject
    ProductRepository productRepository;

    @BeforeEach
    void setUp() throws IOException, SQLException {
        SqlUtils.load(connection, resourceLoader, "sql/seed-data.sql");
    }

    @Test
    void shouldGetAllProducts() {
        List<Product> products = productRepository.findAll();
        assertEquals(2, products.size());
    }

    @Test
    void shouldNotCreateAProductWithDuplicateCode() {
        Product product = new Product(3L, "p101", "Test Product");
        assertDoesNotThrow(() -> productRepository.createProductIfNotExists(product));
        Optional<Product> optionalProduct = productRepository.findById(product.getId());
        assertTrue(optionalProduct.isEmpty());
    }

}
