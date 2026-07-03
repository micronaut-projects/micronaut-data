package io.micronaut.data.jdbc.sqlite.groovy_static_repo;

import io.micronaut.data.jdbc.sqlite.SQLiteDBProperties;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@MicronautTest
@SQLiteDBProperties
class SQLiteGroovyStaticExtendedRepositoryTest {

    @Inject
    TestEntityRepository entityRepository;

    @Test
    void simpleOperation() {
        GTestEntity entity = new GTestEntity();
        entity.setName("xxx");

        assertDoesNotThrow(() -> {
            GTestEntity saved = entityRepository.save(entity);
            entityRepository.update(saved.getId(), "zzz");
        });
    }
}
