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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource;
import io.micronaut.dev.tck.ReloadHarness;
import io.micronaut.dev.tck.ReloadTck;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs a JDBC application on H2 through the development runtime, which restarts the application on a change,
 * and edits an entity (a new field) and its repository (a new query method): the next generation maps the new
 * column, serves the new query, and no retired generation stays reachable.
 *
 * <p>The schema follows an entity change in create-drop mode, whether the in-memory database closes with the pool
 * or outlives it, as a database server does, and in create mode only with a database that closes with the pool.
 * Create mode with a database that outlives the application keeps the previous table, and warns about it: nothing
 * is dropped.</p>
 *
 * <p>With the data source named for retention, the restart still works and leaves nothing of the first generation
 * reachable. The pool is not retained yet, though: the Hikari data source factory of Micronaut SQL holds the context,
 * so the runtime refuses to carry it over, and logs why. Retaining it depends on Micronaut core retaining the
 * unwrapped pool; once it does, create mode with a retained pool is the case that warns, and only the create-drop
 * case of the retained variants follows the entity.</p>
 */
class JdbcRestartReloadTest {

    private static final String SCHEMA_RELOADER = "io.micronaut.data.jdbc.config.DevelopmentSchemaReloader";

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
        // the pool is not retained until Micronaut core retains it: with it retained, create mode keeps the
        // previous table, so only create-drop is expected to follow the entity
        "CREATE_DROP, true, false",
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

    @Test
    void createModeWarnsAboutAChangedEntityAndKeepsItsTable() throws SQLException {
        Logger logger = (Logger) LoggerFactory.getLogger(SCHEMA_RELOADER);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try (ReloadHarness harness = ReloadHarness.inDirectory(project)) {
            database(harness, "CREATE", true);
            DevApplication.first(harness);
            harness.start();
            DevApplication.useFirstGeneration(harness);

            // the book gains a column, which create mode cannot add to the table the database kept
            DevApplication.second(harness);
            harness.reload();
            assertEquals(2, harness.generation());

            List<String> warnings = appender.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
            assertEquals(1, warnings.size(), () -> "One warning expected: " + warnings);
            assertTrue(warnings.get(0).contains("[book]"), warnings.get(0));
            assertTrue(warnings.get(0).contains("datasources.default.schema-generate=CREATE_DROP"), warnings.get(0));
            assertTrue(warnings.get(0).contains("application-dev.properties"), warnings.get(0));

            // nothing was dropped: the table keeps its rows, and its previous columns
            DataSource dataSource = DelegatingDataSource.unwrapDataSource(harness.context().getBean(DataSource.class));
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                try (ResultSet rows = statement.executeQuery("SELECT TITLE FROM BOOK")) {
                    assertTrue(rows.next());
                    assertEquals("One", rows.getString(1));
                    assertFalse(rows.next());
                }
                try (ResultSet columns = connection.getMetaData().getColumns(null, null, "BOOK", "PAGES")) {
                    assertFalse(columns.next(), "create mode does not add the new column");
                }
            }
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void createModeWarnsAboutNothingWhenNoEntityChanged() {
        Logger logger = (Logger) LoggerFactory.getLogger(SCHEMA_RELOADER);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try (ReloadHarness harness = ReloadHarness.inDirectory(project)) {
            database(harness, "CREATE", true);
            DevApplication.first(harness);
            harness.start();
            DevApplication.useFirstGeneration(harness);

            DevApplication.secondRepositoryOnly(harness);
            harness.reload();
            assertEquals(2, harness.generation());
            assertTrue(appender.list.stream().noneMatch(event -> event.getLevel() == Level.WARN), () -> appender.list.toString());
        } finally {
            logger.detachAppender(appender);
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
