package io.micronaut.data.jdbc.sqlite;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@MicronautTest
@SQLiteDBProperties(packages = "io.micronaut.data.jdbc.sqlite")
class SqliteUUIDTest {

    @Inject
    SqliteUuidRepository uuidRepository;

    @AfterEach
    void cleanup() {
        uuidRepository.deleteAll();
    }

    @Test
    void testInsertAndUpdateWithUUID() {
        SqliteUuidEntity test = uuidRepository.save(new SqliteUuidEntity("Fred"));
        UUID uuid = test.getUuid();

        assertNotNull(uuid);

        test = uuidRepository.findById(test.getUuid()).orElse(null);

        assertNotNull(test);
        assertEquals(uuid, test.getUuid());
        assertEquals("Fred", test.getName());

        test = uuidRepository.update(test);

        assertEquals(uuid, test.getUuid());
        assertEquals("Fred", test.getName());
    }

    @Test
    void testInsertAndReturnUuid() {
        SqliteUuidEntity test = uuidRepository.save(new SqliteUuidEntity("Fred"));
        UUID uuid = test.getUuid();

        assertNotNull(uuid);

        test = uuidRepository.findById(test.getUuid()).orElse(null);
        UUID foundUuid = uuidRepository.findUuidByName("Fred");

        assertNotNull(test);
        assertEquals(uuid, foundUuid);
    }

    @Test
    void testInsertAndUpdateNullUuid() {
        SqliteUuidEntity test = uuidRepository.save(new SqliteUuidEntity("Fred", UUID.randomUUID()));
        UUID uuid = test.getUuid();

        assertNotNull(uuid);
        assertNotNull(test.getUuid());

        test.setNullableValue(null);
        SqliteUuidEntity updatedTest = uuidRepository.update(test);

        assertNotNull(updatedTest);
        assertNull(updatedTest.getNullableValue());
    }

    @Test
    void testCriteriaWithNullValue() {
        uuidRepository.save(new SqliteUuidEntity("Fred", null));
        Collection<SqliteUuidEntity> result = uuidRepository.findByNullableValue(null);

        assertEquals(1, result.size());
    }
}
