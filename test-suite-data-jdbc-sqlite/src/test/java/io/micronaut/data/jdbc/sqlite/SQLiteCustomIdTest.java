/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.data.tck.entities.Task;
import io.micronaut.data.tck.entities.TaskGenericEntity;
import io.micronaut.data.tck.entities.TaskGenericEntity2;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest
@SQLiteDBProperties
class SQLiteCustomIdTest {

    @Inject
    SQLiteTaskRepository taskRepository;

    @Inject
    SQLiteTaskGenericEntityRepository taskGenericEntityRepository;

    @Inject
    SQLiteTaskGenericEntity2Repository taskGenericEntity2Repository;

    @Test
    void testSaveAndReadEntity() {
        Task task = taskRepository.save(new Task("Task 1"));
        assertNotNull(task.getTaskId());

        task = taskRepository.findById(task.getTaskId()).orElse(null);
        assertNotNull(task);
        assertNotNull(task.getTaskId());
        assertEquals("Task 1", task.getName());
    }

    @Test
    void testSaveAndReadGenericEntity() {
        TaskGenericEntity task = taskGenericEntityRepository.save(new TaskGenericEntity("Task 1"));
        assertNotNull(task.getId());

        task = taskGenericEntityRepository.findById(task.getId()).orElse(null);
        assertNotNull(task);
        assertNotNull(task.getId());
        assertEquals("Task 1", task.getName());
    }

    @Test
    void testSaveAndReadGenericEntity2() {
        TaskGenericEntity2 task = taskGenericEntity2Repository.save(new TaskGenericEntity2("Task 1"));
        assertNotNull(task.getId());

        task = taskGenericEntity2Repository.findById(task.getId()).orElse(null);
        assertNotNull(task);
        assertNotNull(task.getId());
        assertEquals("Task 1", task.getName());
    }
}
