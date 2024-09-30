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

import com.tangosol.net.NamedMap;
import com.tangosol.net.Session;
import com.tangosol.util.UUID;
import io.micronaut.coherence.data.model.Book;
import io.micronaut.coherence.data.repositories.CoherenceBook2Repository;
import io.micronaut.coherence.data.repositories.CoherenceBook3Repository;
import io.micronaut.coherence.data.repositories.CoherenceBookRepository;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Validation of {@link AbstractCoherenceRepository}.
 */
@MicronautTest(propertySources = {"classpath:sessions.yaml"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RepositoryTest extends AbstractDataTest {

    /**
     * Defined in configuration and using the default Session.
     */
    @Inject
    protected CoherenceBookRepository repo;

    /**
     * Defined in configuration and using the {@code custom} Session.
     */
    @Inject
    protected CoherenceBook2Repository repo2;

    /**
     * Not defined in configuration using the default Session.
     */
    @Inject
    protected CoherenceBook3Repository repo3;

    /**
     * Custom {@code Session}.
     */
    @Inject
    protected Session custom;

    // ----- test methods ---------------------------------------------------

    /**
     * Ensure the {@link io.micronaut.coherence.data.interceptors.GetMapInterceptor} interceptor fires when
     * {@link AbstractCoherenceRepository#getMap()} is invoked.
     */
    @Test
    void shouldReturnNamedMap() {
        Assertions.assertNotNull(repo);
        Assertions.assertInstanceOf(NamedMap.class, repo.getMap());
        Assertions.assertEquals(4L, repo.count());
    }

    /**
     * Ensure the {@link io.micronaut.coherence.data.interceptors.GetIdInterceptor} interceptor fires when
     * {@link AbstractCoherenceRepository#getId(Object)} is invoked.
     */
    @Test
    void shouldReturnId() {
        Assertions.assertNotNull(repo);
        UUID id = repo.getId(DUNE);
        Assertions.assertEquals(DUNE.getUuid(), id);
    }

    /**
     * Ensure the {@link io.micronaut.coherence.data.interceptors.GetEntityTypeInterceptor} interceptor fires when
     * {@link AbstractCoherenceRepository#getEntityType()} is invoked.
     */
    @Test
    void shouldReturnEntityType() {
        Assertions.assertNotNull(repo);
        Assertions.assertEquals(Book.class, repo.getEntityType());
    }

    /**
     * Ensure generated queries continue to work when extending {@code AbstractCoherenceRepository}.
     */
    @Test
    void shouldAllowGeneratedQueries() {
        Assertions.assertTrue(repo.findByTitleStartingWith("Du").containsAll(
                books.stream().filter(book -> book.getTitle().startsWith("Du")).toList()));
    }

    /**
     * Ensure custom session is used properly.
     *
     * @since 3.0.1
     */
    @Test
    void shouldUseCustomSession() {
        Assertions.assertNotNull(repo);
        Assertions.assertEquals(Book.class, repo2.getEntityType());
        Assertions.assertEquals(4L, custom.getMap("book2").size());
    }
}
