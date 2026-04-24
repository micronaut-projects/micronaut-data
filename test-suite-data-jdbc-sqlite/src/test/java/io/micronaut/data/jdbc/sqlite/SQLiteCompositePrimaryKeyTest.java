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

import io.micronaut.data.tck.jdbc.entities.Project;
import io.micronaut.data.tck.jdbc.entities.ProjectId;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
@SQLiteDBProperties
class SQLiteCompositePrimaryKeyTest {

    @Inject
    SQLiteProjectRepository projectRepository;

    @Test
    void testCrudWithCompositeId() {
        ProjectId id = new ProjectId(10, 1);
        Project p = new Project(id, "Project 1");
        p.setOrg("test");
        Project project = projectRepository.save(p);

        assertEquals(10, project.getProjectId().getDepartmentId());
        assertEquals(1, project.getProjectId().getProjectId());

        project = projectRepository.findAll().iterator().next();
        assertEquals(10, project.getProjectId().getDepartmentId());
        assertEquals(1, project.getProjectId().getProjectId());

        project = projectRepository.findById(id).orElse(null);
        assertNotNull(project);
        assertEquals(10, project.getProjectId().getDepartmentId());
        assertEquals(1, project.getProjectId().getProjectId());
        assertEquals("project 1", project.getName());
        assertTrue(projectRepository.existsById(id));

        projectRepository.update(id, "Project Changed");
        project = projectRepository.findById(id).orElse(null);
        assertNotNull(project);
        assertEquals("project changed", project.getName());
        assertEquals("PROJECT CHANGED", project.getDbName());

        projectRepository.deleteById(id);
        project = projectRepository.findById(id).orElse(null);
        assertFalse(project != null);
    }
}
