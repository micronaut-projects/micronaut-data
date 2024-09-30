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

import com.tangosol.net.AsyncNamedMap;
import io.micronaut.coherence.data.model.Book;
import io.micronaut.coherence.data.repositories.CoherenceAsyncBookRepository;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Validation of {@link AbstractCoherenceAsyncRepository}.
 */
@MicronautTest(propertySources = {"classpath:sessions.yaml"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AsyncRepositoryTest extends AbstractDataTest {

    /**
     * The concrete {@link AbstractCoherenceAsyncRepository} implementation under test.
     */
    @Inject
    protected CoherenceAsyncBookRepository repo;

    // ----- test methods ---------------------------------------------------

    /**
     * Ensure the {@link io.micronaut.coherence.data.interceptors.GetMapInterceptor} interceptor fires when
     * {@link AbstractCoherenceAsyncRepository#getMap()} is invoked.
     */
    @Test
    public void shouldReturnNamedMap() {
        Assertions.assertNotNull(repo);
        Assertions.assertTrue(repo.getMap() instanceof AsyncNamedMap);
        Assertions.assertEquals(4L, repo.count().join());
    }

    /**
     * Ensure the {@link io.micronaut.coherence.data.interceptors.GetIdInterceptor} interceptor fires when
     * {@link AbstractCoherenceAsyncRepository#getId(Object)} is invoked.
     */
    @Test
    public void shouldReturnId() {
        Assertions.assertNotNull(repo);
        Assertions.assertEquals(DUNE.getUuid(), repo.getId(DUNE));
    }

    /**
     * Ensure the {@link io.micronaut.coherence.data.interceptors.GetEntityTypeInterceptor} interceptor fires when
     * {@link AbstractCoherenceAsyncRepository#getEntityType()} is invoked.
     */
    @Test
    public void shouldReturnEntityType() {
        Assertions.assertNotNull(repo);
        Assertions.assertEquals(Book.class, repo.getEntityType());
    }

    /**
     * Ensure generated queries continue to work when extending {@code AbstractCoherenceAsyncRepository}.
     */
    @Test
    public void shouldAllowGeneratedQueries() {
        repo.findByTitleStartingWith("Du")
                .thenAccept(books1 -> Assertions.assertTrue(books1.containsAll(books.stream().filter(book -> book.getTitle().startsWith("Du")).toList())))
                .join();
    }
}
