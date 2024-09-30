package io.micronaut.coherence.data;

import com.tangosol.util.UUID;
import io.micronaut.coherence.data.model.Book;
import io.micronaut.coherence.data.repositories.AsyncBookRepository;
import io.micronaut.coherence.data.repositories.BookRepository;
import io.micronaut.coherence.data.repositories.CoherenceAsyncBookRepository;
import io.micronaut.coherence.data.repositories.CoherenceBookRepository;
import io.micronaut.coherence.data.util.EventRecord;
import io.micronaut.coherence.data.util.EventType;
import io.micronaut.context.BeanContext;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.async.AsyncCrudRepository;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Validate persist event eviction logic.
 */
@MicronautTest(propertySources = {"classpath:sessions.yaml"}, environments = "evict-persist")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PersistEventEvictionsTest extends AbstractDataTest {
    /**
     * A sync repo that extends {@link AbstractCoherenceRepository}.
     */
    @Inject
    protected CoherenceBookRepository repo;

    /**
     * A {@code repository} implementing {@link CrudRepository}.
     */
    @Inject
    protected BookRepository crudRepo;

    /**
     * A sync repo that extends {@link AbstractCoherenceAsyncRepository}.
     */
    @Inject
    protected CoherenceAsyncBookRepository repoAsync;

    /**
     * A {@code repository} implementing {@link AsyncCrudRepository}.
     */
    @Inject
    protected AsyncBookRepository crudRepoAsync;

    /**
     * Micronaut {@link BeanContext}.
     */
    @Inject
    protected BeanContext beanContext;

    // ----- test methods ---------------------------------------------------

    /**
     * Validate event listener returning false results in the entity not being persisted using {@link #crudRepo}.
     */
    @Test
    void shouldValidatePrePersistEvictionSyncRepo() {
        runPersistEventTestEviction(crudRepo);
    }

    /**
     * Validate event listener returning false results in the entity not being persisted using {@link #crudRepoAsync}.
     */
    @Test
    void shouldValidatePrePersistEvictionAsyncRepo() {
        runPersistEventTestEviction(crudRepoAsync);
    }

    // ----- helper methods -------------------------------------------------

    /**
     * Validate eviction behavior.
     *
     * @param repository the {@link CrudRepository} under test
     */
    private void runPersistEventTestEviction(CrudRepository<Book, UUID> repository) {
        Assertions.assertFalse(repository.existsById(IT.getUuid()));
        Book result = repository.save(IT);
        Assertions.assertEquals(IT, result);
        Assertions.assertFalse(repository.existsById(IT.getUuid()));
        Assertions.assertTrue(eventRecorder.getRecordedEvents().contains(
                new EventRecord<>(EventType.PRE_PERSIST, IT)));
    }

    /**
     * Validate eviction behavior.
     *
     * @param repository the {@link AsyncCrudRepository} under test
     */
    private void runPersistEventTestEviction(AsyncCrudRepository<Book, UUID> repository) {
        repository.existsById(IT.getUuid())
                .thenAccept(Assertions::assertFalse)
                .thenCompose(unused -> repository.save(IT))
                .thenAccept(book1 -> Assertions.assertEquals(IT, book1))
                .thenCompose(unused -> repository.existsById(IT.getUuid()))
                .thenAccept(Assertions::assertFalse)
                .thenAccept(unused -> Assertions.assertTrue(eventRecorder.getRecordedEvents().contains(
                        new EventRecord<>(EventType.PRE_PERSIST, IT)))).join();
    }
}
