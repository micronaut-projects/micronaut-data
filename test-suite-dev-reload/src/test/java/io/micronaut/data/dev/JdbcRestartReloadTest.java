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
package io.micronaut.data.dev;

import io.micronaut.dev.tck.ReloadHarness;
import io.micronaut.dev.tck.ReloadTck;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Runs a JDBC application on H2 through the development runtime, which restarts the application on a change,
 * and edits an entity (a new field) and its repository (a new query method): the next generation maps the new
 * column, serves the new query, and no retired generation stays reachable. The schema is generated in create and
 * in create-drop mode, with an in-memory database that closes with the pool and with one that outlives it, as a
 * database server does.
 *
 * <p>With the data source named for retention, the restart still works and leaves nothing of the first generation
 * reachable. The pool is not retained, though: the Hikari data source factory of Micronaut SQL holds the context,
 * so the runtime refuses to carry it over, and logs why.</p>
 */
class JdbcRestartReloadTest {

    @TempDir
    Path project;

    @BeforeAll
    static void initializeH2() throws ClassNotFoundException {
        // H2 preallocates an exception as it initializes TraceObject, whose stack trace would otherwise hold the
        // frames of the first generation that opens a connection, and with them its classes
        Class.forName("org.h2.message.TraceObject", true, JdbcRestartReloadTest.class.getClassLoader());
    }

    @ParameterizedTest(name = "schema-generate={0}, retain data source={1}, database outlives the pool={2}")
    @CsvSource({
        "CREATE_DROP, false, false",
        "CREATE, false, false",
        "CREATE_DROP, false, true",
        "CREATE, false, true",
        "CREATE_DROP, true, false",
        "CREATE, true, false",
    })
    void entityAndRepositoryChangesAreServedByTheNextGeneration(String schemaGenerate, boolean retainDataSource, boolean persistentDatabase) {
        try (ReloadHarness harness = ReloadHarness.inDirectory(project)) {
            database(harness, schemaGenerate, persistentDatabase);
            if (retainDataSource) {
                harness.retain(DataSource.class.getName());
            }
            DevApplication.first(harness);
            harness.start();
            // the first generation's context is used in a method of its own, so that no local of this one keeps it reachable
            DevApplication.useFirstGeneration(harness);

            DevApplication.second(harness);
            harness.reload();
            assertEquals(2, harness.generation());

            // the new column is mapped, and the new query method served
            DevApplication.useSecondGeneration(harness);
            ReloadTck.assertFollowsReload(harness, DevApplication::repository);

            // neither the entity registry, the query caches nor the schema reloader keep the first generation reachable
            ReloadTck.assertRetiredGenerationsCollected(harness);
        }
    }

    @Test
    void createModeKeepsTheRowsOfTheEntitiesThatDidNotChange() {
        try (ReloadHarness harness = ReloadHarness.inDirectory(project)) {
            database(harness, "CREATE", true);
            DevApplication.first(harness);
            harness.start();
            DevApplication.useFirstGeneration(harness);

            // the repository gains a query method, the entity is the same
            DevApplication.secondRepositoryOnly(harness);
            harness.reload();
            assertEquals(2, harness.generation());

            assertEquals(List.of("One"), DevApplication.titles(harness.context()));
            assertEquals(List.of("One"), DevApplication.titled(harness.context(), "One"));
            ReloadTck.assertRetiredGenerationsCollected(harness);
        }
    }

    private static void database(ReloadHarness harness, String schemaGenerate, boolean persistentDatabase) {
        harness.property("datasources.default.url", "jdbc:h2:mem:" + UUID.randomUUID() + ";LOCK_TIMEOUT=10000" + (persistentDatabase ? ";DB_CLOSE_DELAY=-1" : ""));
        harness.property("datasources.default.username", "sa");
        harness.property("datasources.default.password", "");
        harness.property("datasources.default.driver-class-name", "org.h2.Driver");
        harness.property("datasources.default.dialect", "H2");
        harness.property("datasources.default.schema-generate", schemaGenerate);
    }
}
