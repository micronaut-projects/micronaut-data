/*
 * Copyright 2017-2025 original authors
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

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
@SQLiteDBProperties
@Property(name = "sqlite.sql-recorder.enabled", value = "true")
class GeneratedValueTest {

    @Inject
    GeneratedValueRepository repository;

    @AfterEach
    void cleanup() {
        repository.deleteAll();
    }

    @Test
    void testSaveAndLoadGeneratedIdentity() {
        GeneratedValueEntity saved = repository.save(new GeneratedValueEntity("alpha"));

        assertNotNull(saved.getId());

        GeneratedValueEntity reloaded = repository.findById(saved.getId()).orElse(null);

        assertNotNull(reloaded);
        assertEquals(saved.getId(), reloaded.getId());
        assertEquals("alpha", reloaded.getName());
    }

    @Test
    void testInsertReturningAssignsGeneratedIdentity() {
        RecordedSql.clear();
        GeneratedValueEntity inserted = repository.insertReturning(new GeneratedValueEntity("gamma"));

        assertTrue(RecordedSql.hasStatementContaining("INSERT", "RETURNING"), RecordedSql.statements().toString());
        assertNotNull(inserted.getId());
        assertEquals("gamma", inserted.getName());

        GeneratedValueEntity reloaded = repository.findById(inserted.getId()).orElse(null);

        assertNotNull(reloaded);
        assertEquals(inserted.getId(), reloaded.getId());
        assertEquals("gamma", reloaded.getName());
    }

    @Test
    void testSaveAllAssignsGeneratedIdentities() {
        List<GeneratedValueEntity> saved = repository.saveAll(List.of(
            new GeneratedValueEntity("alpha"),
            new GeneratedValueEntity("beta")
        ));

        saved.forEach(entity -> assertNotNull(entity.getId()));
        assertEquals(2, saved.stream().map(GeneratedValueEntity::getId).distinct().count());

        List<GeneratedValueEntity> reloaded = saved.stream()
            .map(entity -> repository.findById(entity.getId()).orElse(null))
            .toList();

        Set<String> names = reloaded.stream().map(GeneratedValueEntity::getName).collect(Collectors.toSet());
        assertEquals(Set.of("alpha", "beta"), names);
    }
}
