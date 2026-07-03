package io.micronaut.data.jdbc.sqlite;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@MicronautTest(rollback = false)
@SQLiteDBProperties
class ChallengeRepositoryTest {

    @Inject
    ChallengeRepository repository;

    @Test
    void queryWithMultipleJoinsIsSuccessful() {
        assertDoesNotThrow(() -> repository.findById(1L));
    }
}
