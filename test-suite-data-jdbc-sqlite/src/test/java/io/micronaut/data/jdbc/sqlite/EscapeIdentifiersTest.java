/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.data.jdbc.sqlite;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(rollback = false)
@JavaSQLiteDBProperties
class EscapeIdentifiersTest {

    @Inject
    SQLiteTableRatingsRepository repository;

    @Test
    void testSaveOne() {
        TableRatings ratings = new TableRatings(10);
        repository.save(ratings);

        assertNotNull(ratings.getId());
        assertTrue(repository.findById(ratings.getId()).isPresent());
        assertTrue(repository.existsById(ratings.getId()));
        assertEquals(1, repository.count());
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void testSaveMany() {
        TableRatings p1 = repository.save(new TableRatings(20));
        TableRatings p2 = repository.save(new TableRatings(30));
        List<TableRatings> ratings = List.of(p1, p2);

        assertTrue(ratings.stream().allMatch(r -> r.getId() != null));
        assertTrue(ratings.stream().allMatch(r -> repository.findById(r.getId()).isPresent()));
        assertEquals(3, repository.findAll().size());
        assertEquals(3, repository.count());
    }

    @Test
    void testDeleteById() {
        TableRatings rating = repository.findByRating(20);

        assertNotNull(rating);
        assertEquals(20, rating.getRating());
        assertTrue(repository.findById(rating.getId()).isPresent());

        repository.deleteById(rating.getId());

        assertTrue(repository.findById(rating.getId()).isEmpty());
        assertEquals(2, repository.count());
    }

    @Test
    void testUpdateOne() {
        TableRatings ratings = repository.findByRating(10);

        assertNotNull(ratings);

        repository.updateRating(ratings.getId(), 15);

        assertEquals(null, repository.findByRating(10));
        assertNotNull(repository.findByRating(15));
    }
}
