/*
 * Copyright 2017-2024 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.coherence.data;

import com.tangosol.util.UUID;
import io.micronaut.coherence.data.model.Book;
import io.micronaut.coherence.data.model.MutationsBook;
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

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Validate entity events.
 */
@MicronautTest(propertySources = {"classpath:sessions.yaml"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EventsTest extends AbstractDataTest {
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
     * Validate pre/post persist events are triggered for {@link #crudRepo}.
     */
    @Test
    void shouldTriggerPreAndPostPersistEventsSyncRepo() {
        runPersistEventTest(crudRepo);
    }

    /**
     * Validate pre/post persist events are triggered for {@link #crudRepoAsync}.
     */
    @Test
    void shouldTriggerPreAndPostPersistEventsAsyncRepo() throws Exception {
        runPersistEventTest(crudRepoAsync);
    }

    /**
     * Validate validate mutations made in pre persist event are saved against {@link #crudRepo}.
     */
    @Test
    void shouldValidatePrePersistMutationsSyncRepo() {
        runPersistEventTestMutations(crudRepo);
    }

    /**
     * Validate validate mutations made in pre persist event are saved against {@link #crudRepoAsync}.
     */
    @Test
    void shouldValidatePrePersistMutationsAsyncRepo() throws Exception {
        runPersistEventTestMutations(crudRepoAsync);
    }

    /**
     * Validate pre/post persist events are triggered for {@link #crudRepo}.
     */
    @Test
    void shouldTriggerPreAndPostUpdateEventsCrud() {
        Book duneUpdate = new Book(DUNE);
        duneUpdate.setPages(1000);

        crudRepo.update(duneUpdate);

        Assertions.assertTrue(eventRecorder.getRecordedEvents().containsAll(
            List.of(
                new EventRecord<>(EventType.PRE_UPDATE, duneUpdate),
                new EventRecord<>(EventType.POST_UPDATE, duneUpdate))));
    }

    /**
     * Validate pre/post remove events are triggered for {@link #crudRepo}.
     */
    @Test
    void shouldTriggerPreAndPostRemoveEventsSyncRepo() {
        runRemoveEventTest(crudRepo);
    }

    /**
     * Validate pre/post remove events are triggered for {@link #crudRepoAsync}.
     */
    @Test
    void shouldTriggerPreAndPostRemoveEventsAsyncRepo() throws Exception {
        runRemoveEventTest(crudRepoAsync);
    }

    // ----- helper methods -------------------------------------------------

    /**
     * Validate per/post persist.
     *
     * @param repository the {@link CrudRepository} under test
     */
    private void runPersistEventTest(CrudRepository<Book, UUID> repository) {
        repository.save(IT);

        Assertions.assertTrue(eventRecorder.getRecordedEvents().containsAll(
            List.of(
                new EventRecord<>(EventType.PRE_PERSIST, IT),
                new EventRecord<>(EventType.POST_PERSIST, IT))));
    }

    /**
     * Validate per/post persist.
     *
     * @param repository the {@link AsyncCrudRepository} under test
     */
    private void runPersistEventTest(AsyncCrudRepository<Book, UUID> repository) throws Exception {
        repository.save(IT).thenAccept(unused -> Assertions.assertTrue(eventRecorder.getRecordedEvents().containsAll(
            List.of(
                new EventRecord<>(EventType.PRE_PERSIST, IT),
                new EventRecord<>(EventType.POST_PERSIST, IT))))).get(5, TimeUnit.SECONDS);
    }

    /**
     * Validate per/post persist mutations.
     *
     * @param repository the {@link CrudRepository} under test
     */
    private void runPersistEventTestMutations(CrudRepository<Book, UUID> repository) {
        MutationsBook b = beanContext.inject(new MutationsBook(IT));
        Book result = repository.save(b); // should trigger setting page count on b to 1000

        Assertions.assertEquals(result, b);
        Assertions.assertTrue(eventRecorder.getRecordedEvents().containsAll(
            List.of(
                new EventRecord<>(EventType.PRE_PERSIST, IT),
                new EventRecord<>(EventType.PRE_PERSIST, result),
                new EventRecord<>(EventType.POST_PERSIST, result))));
    }

    /**
     * Validate per/post persist mutations.
     *
     * @param repository the {@link AsyncCrudRepository} under test
     */
    private void runPersistEventTestMutations(AsyncCrudRepository<Book, UUID> repository) throws Exception {
        MutationsBook b = beanContext.inject(new MutationsBook(IT));
        repository.save(b)
            .thenApply(b1 -> {
                Assertions.assertEquals(b1, b);
                return b1;
            }).thenAccept(result -> Assertions.assertTrue(eventRecorder.getRecordedEvents().containsAll(
                List.of(
                    new EventRecord<>(EventType.PRE_PERSIST, IT),
                    new EventRecord<>(EventType.PRE_PERSIST, result),
                    new EventRecord<>(EventType.POST_PERSIST, result))))).get(5, TimeUnit.SECONDS);
    }

    /**
     * Validate per/post remove.
     *
     * @param repository the {@link CrudRepository} under test
     */
    private void runRemoveEventTest(CrudRepository<Book, UUID> repository) {
        repository.delete(DUNE);

        Assertions.assertTrue(eventRecorder.getRecordedEvents().containsAll(
            List.of(
                new EventRecord<>(EventType.PRE_REMOVE, DUNE),
                new EventRecord<>(EventType.POST_REMOVE, DUNE))));
    }

    /**
     * Validate per/post remove.
     *
     * @param repository the {@link AsyncCrudRepository} under test
     */
    private void runRemoveEventTest(AsyncCrudRepository<Book, UUID> repository) throws Exception {
        repository.delete(DUNE).thenAccept(unused -> Assertions.assertTrue(eventRecorder.getRecordedEvents().containsAll(
            List.of(
                new EventRecord<>(EventType.PRE_REMOVE, DUNE),
                new EventRecord<>(EventType.POST_REMOVE, DUNE))))).get(5, TimeUnit.SECONDS);
    }
}
