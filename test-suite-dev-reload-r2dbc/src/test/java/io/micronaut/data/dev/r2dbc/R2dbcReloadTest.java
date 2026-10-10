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
package io.micronaut.data.dev.r2dbc;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.reload.ClassChange;
import io.micronaut.context.reload.ClassChangeEvent;
import io.micronaut.context.reload.ReloadStrategy;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.r2dbc.config.R2dbcSchemaGenerator;
import io.micronaut.dev.tck.ReloadHarness;
import io.micronaut.dev.tck.ReloadTck;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs an R2DBC application on H2 through the development runtime, with the connection pool that Micronaut R2DBC
 * retains across restarts, and edits an entity (a new field) and its repository (a new query method).
 *
 * <p>The pool keeps the in-memory database open across the restart, so the database outlives the application, as a
 * database server does: in create-drop mode the schema follows the entity, and in create mode the previous table is
 * kept, with a warning. Either way the next generation is served by the same pool, and no retired generation stays
 * reachable.</p>
 */
class R2dbcReloadTest {

    private static final String SCHEMA_RELOADER = "io.micronaut.data.r2dbc.config.DevelopmentR2dbcSchemaReloader";
    private static final String BOOK = "example.Book";
    private static final String REPOSITORY = "example.BookRepository";
    private static final String LIBRARY = "example.Library";

    @TempDir
    Path project;

    @BeforeAll
    static void initializeH2() throws ClassNotFoundException {
        // H2 preallocates an exception as it initializes TraceObject, whose stack trace would otherwise hold the
        // frames of the first generation that opens a connection, and with them its classes
        Class.forName("org.h2.message.TraceObject", true, R2dbcReloadTest.class.getClassLoader());
    }

    @Test
    void anEntityAndRepositoryChangeIsServedByTheNextGenerationOnTheRetainedPool() {
        try (ReloadHarness harness = ReloadHarness.inDirectory(project)) {
            database(harness, "CREATE_DROP");
            first(harness);
            harness.start();
            ConnectionPool pool = pool(harness.context());
            useFirstGeneration(harness.context());

            second(harness);
            harness.reload();
            assertEquals(2, harness.generation());

            // the pool, and the in-memory database it keeps open, are those of the first generation
            ReloadTck.assertRetained(harness, pool);
            assertSame(pool, pool(harness.context()));
            assertFalse(pool.isDisposed());

            // create-drop generated the table again, with the new column, and the new query method is served
            add(harness.context(), "Two", 200);
            add(harness.context(), "Three", 300);
            assertEquals(List.of("Three"), longerThan(harness.context(), 250));
            ReloadTck.assertFollowsReload(harness, R2dbcReloadTest::repository);

            ReloadTck.assertRetiredGenerationsCollected(harness);
        }
    }

    @Test
    void createModeWarnsAboutAChangedEntityAndKeepsTheTableOfTheRetainedPool() {
        Logger logger = (Logger) LoggerFactory.getLogger(SCHEMA_RELOADER);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try (ReloadHarness harness = ReloadHarness.inDirectory(project)) {
            database(harness, "CREATE");
            first(harness);
            harness.start();
            ConnectionPool pool = pool(harness.context());
            useFirstGeneration(harness.context());

            second(harness);
            harness.reload();
            assertEquals(2, harness.generation());
            assertSame(pool, pool(harness.context()));

            List<String> warnings = appender.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
            assertEquals(1, warnings.size(), () -> "One warning expected: " + warnings);
            assertTrue(warnings.get(0).contains("[book]"), warnings.get(0));
            assertTrue(warnings.get(0).contains("r2dbc.datasources.default.schema-generate=CREATE_DROP"), warnings.get(0));

            // nothing was dropped: the table keeps its row, and its previous columns
            assertEquals(1L, count(pool, "SELECT COUNT(*) FROM BOOK"));
            assertEquals(0L, count(pool, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'BOOK' AND COLUMN_NAME = 'PAGES'"));
            ReloadTck.assertRetiredGenerationsCollected(harness);
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void aReloadInPlaceRecreatesTheDataBeansOnTheSamePoolAndGeneratesTheSchemaAgainWhenItRetiresAClassloader() {
        try (ReloadHarness harness = ReloadHarness.inDirectory(project)) {
            database(harness, "CREATE");
            first(harness);
            ApplicationContext context = harness.start();
            ConnectionPool pool = pool(context);
            useFirstGeneration(context);
            Object operations = context.getBean(RepositoryOperations.class);
            Object generator = context.getBean(R2dbcSchemaGenerator.class);
            Object library = context.getBean(type(context, LIBRARY));
            ClassLoader loader = context.getClassLoader();

            // the entity redefined in place: the data beans are recreated, the schema and the pool kept
            context.publishEvent(new ClassChangeEvent(this, Set.of(), loader, List.of(new ClassChange(BOOK, ClassChange.Kind.MODIFIED)), ReloadStrategy.RELOAD));
            assertNotSame(operations, context.getBean(RepositoryOperations.class));
            assertNotSame(library, context.getBean(type(context, LIBRARY)));
            assertSame(generator, context.getBean(R2dbcSchemaGenerator.class));
            assertSame(pool, pool(context));
            assertEquals(List.of("One"), titles(context));

            // a classloader retired in place: the schema is generated again, on the same pool, which keeps the rows
            context.publishEvent(new ClassChangeEvent(this, Set.of(loader), loader, List.of(), ReloadStrategy.RELOAD));
            assertNotSame(generator, context.getBean(R2dbcSchemaGenerator.class));
            assertSame(pool, pool(context));
            assertEquals(List.of("One"), titles(context));
            add(context, "Two");
            assertEquals(List.of("One", "Two"), titles(context));
        }
    }

    private static void database(ReloadHarness harness, String schemaGenerate) {
        harness.property("r2dbc.datasources.default.url", "r2dbc:pool:h2:mem:///" + UUID.randomUUID());
        harness.property("r2dbc.datasources.default.options.initial-size", "1");
        harness.property("r2dbc.datasources.default.dialect", "H2");
        harness.property("r2dbc.datasources.default.schema-generate", schemaGenerate);
    }

    private static void first(ReloadHarness harness) {
        harness.source(BOOK, """
            package example;

            import io.micronaut.data.annotation.GeneratedValue;
            import io.micronaut.data.annotation.Id;
            import io.micronaut.data.annotation.MappedEntity;

            @MappedEntity
            public class Book {
                @Id
                @GeneratedValue
                private Long id;
                private String title;

                public Long getId() { return id; }
                public void setId(Long id) { this.id = id; }
                public String getTitle() { return title; }
                public void setTitle(String title) { this.title = title; }
            }
            """);
        harness.source(REPOSITORY, """
            package example;

            import io.micronaut.data.model.query.builder.sql.Dialect;
            import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
            import io.micronaut.data.repository.CrudRepository;

            @R2dbcRepository(dialect = Dialect.H2)
            public interface BookRepository extends CrudRepository<Book, Long> {
            }
            """);
        harness.source(LIBRARY, """
            package example;

            import java.util.ArrayList;
            import java.util.List;

            @jakarta.inject.Singleton
            public class Library {
                private final BookRepository books;

                public Library(BookRepository books) {
                    this.books = books;
                }

                public long add(String title) {
                    Book book = new Book();
                    book.setTitle(title);
                    return books.save(book).getId();
                }

                public List<String> titles() {
                    List<String> titles = new ArrayList<>();
                    for (Book book : books.findAll()) {
                        titles.add(book.getTitle());
                    }
                    titles.sort(null);
                    return titles;
                }
            }
            """);
    }

    private static void second(ReloadHarness harness) {
        harness.source(BOOK, """
            package example;

            import io.micronaut.data.annotation.GeneratedValue;
            import io.micronaut.data.annotation.Id;
            import io.micronaut.data.annotation.MappedEntity;

            @MappedEntity
            public class Book {
                @Id
                @GeneratedValue
                private Long id;
                private String title;
                private int pages;

                public Long getId() { return id; }
                public void setId(Long id) { this.id = id; }
                public String getTitle() { return title; }
                public void setTitle(String title) { this.title = title; }
                public int getPages() { return pages; }
                public void setPages(int pages) { this.pages = pages; }
            }
            """);
        harness.source(REPOSITORY, """
            package example;

            import io.micronaut.data.model.query.builder.sql.Dialect;
            import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
            import io.micronaut.data.repository.CrudRepository;

            import java.util.List;

            @R2dbcRepository(dialect = Dialect.H2)
            public interface BookRepository extends CrudRepository<Book, Long> {
                List<Book> findByPagesGreaterThan(int pages);
            }
            """);
        harness.source(LIBRARY, """
            package example;

            import java.util.ArrayList;
            import java.util.List;

            @jakarta.inject.Singleton
            public class Library {
                private final BookRepository books;

                public Library(BookRepository books) {
                    this.books = books;
                }

                public long add(String title, int pages) {
                    Book book = new Book();
                    book.setTitle(title);
                    book.setPages(pages);
                    return books.save(book).getId();
                }

                public List<String> longerThan(int pages) {
                    List<String> titles = new ArrayList<>();
                    for (Book book : books.findByPagesGreaterThan(pages)) {
                        titles.add(book.getTitle());
                    }
                    return titles;
                }
            }
            """);
    }

    /**
     * Saves a book and reads it back through the first version, in a method of its own, so that no local of the
     * test keeps the first generation reachable.
     */
    private static void useFirstGeneration(ApplicationContext context) {
        assertEquals(1L, add(context, "One"));
        assertEquals(List.of("One"), titles(context));
    }

    private static ConnectionPool pool(ApplicationContext context) {
        return (ConnectionPool) context.getBean(ConnectionFactory.class);
    }

    private static long count(ConnectionFactory connectionFactory, String sql) {
        return Mono.usingWhen(connectionFactory.create(),
                connection -> Flux.from(connection.createStatement(sql).execute())
                    .flatMap(result -> result.map((row, metadata) -> ((Number) row.get(0)).longValue()))
                    .single(),
                Connection::close)
            .block();
    }

    private static long add(ApplicationContext context, String title) {
        return (Long) invoke(context, "add", new Class<?>[] {String.class}, title);
    }

    private static long add(ApplicationContext context, String title, int pages) {
        return (Long) invoke(context, "add", new Class<?>[] {String.class, int.class}, title, pages);
    }

    @SuppressWarnings("unchecked")
    private static List<String> titles(ApplicationContext context) {
        return (List<String>) invoke(context, "titles", new Class<?>[0]);
    }

    @SuppressWarnings("unchecked")
    private static List<String> longerThan(ApplicationContext context, int pages) {
        return (List<String>) invoke(context, "longerThan", new Class<?>[] {int.class}, pages);
    }

    private static Object repository(ApplicationContext context) {
        return context.getBean(type(context, REPOSITORY));
    }

    private static Class<?> type(ApplicationContext context, String className) {
        try {
            return Class.forName(className, true, context.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new AssertionError(className + " is not in the application", e);
        }
    }

    private static Object invoke(ApplicationContext context, String method, Class<?>[] parameterTypes, Object... args) {
        Class<?> library = type(context, LIBRARY);
        try {
            return library.getMethod(method, parameterTypes).invoke(context.getBean(library), args);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot invoke " + method, e);
        }
    }
}
