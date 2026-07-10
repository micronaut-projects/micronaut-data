package example;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest(transactional = false)
class TimestampedRecordRepositoryTest {

    @Inject
    TimestampedRecordRepository repository;

    @AfterEach
    void cleanup() {
        repository.deleteAll();
    }

    // tag::pre-persist-listener-usage[]
    @Test
    void testPrePersistListenerCanVeto() {
        repository.saveAll(List.of(
            new TimestampedRecord("keep"),
            new TimestampedRecord("veto-me")
        ));

        List<TimestampedRecord> records = repository.findAll();

        assertEquals(1, records.size());
        assertEquals("keep", records.get(0).getName());
    }
    // end::pre-persist-listener-usage[]
}
