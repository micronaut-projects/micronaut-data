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
package io.micronaut.data.dev.mongodb;

import com.mongodb.client.MongoClient;
import io.micronaut.configuration.mongo.core.DefaultMongoConfiguration;
import io.micronaut.configuration.mongo.core.dev.RetainedMongoClient;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.reload.ClassChange;
import io.micronaut.context.reload.ClassChangeEvent;
import io.micronaut.context.reload.ReloadStrategy;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.dev.tck.ReloadHarness;
import io.micronaut.dev.tck.ReloadTck;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.MongoDBContainer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs a Micronaut Data MongoDB application through the development runtime, with the driver client that Micronaut
 * MongoDB retains across restarts, and edits an entity (a new field) and its repository (a new query method): the
 * next generation encodes and decodes the changed entity on the retained client.
 *
 * <p>An entity changed in place recreates the data beans; the MongoDB configuration and the retained driver client
 * are kept, and the client bean of the generation builds its codecs again from the new entity registry.</p>
 */
class MongoDataReloadTest {

    private static final String BOOK = "example.Book";
    private static final String REPOSITORY = "example.BookRepository";
    private static final String LIBRARY = "example.Library";

    private static MongoDBContainer mongo;

    @TempDir
    Path project;

    @BeforeAll
    static void startMongo() {
        mongo = new MongoDBContainer("mongo:5");
        mongo.start();
    }

    @AfterAll
    static void stopMongo() {
        if (mongo != null) {
            mongo.stop();
        }
    }

    @Test
    void aChangedEntityIsEncodedAndDecodedByTheNextGenerationOnTheRetainedClient() {
        try (ReloadHarness harness = ReloadHarness.inDirectory(project)) {
            database(harness);
            first(harness);
            harness.start();
            List<RetainedMongoClient> retained = retained(harness.context());
            assertEquals(2, retained.size(), "a retained sync and reactive client");
            useFirstGeneration(harness.context());

            second(harness);
            harness.reload();
            assertEquals(2, harness.generation());

            for (RetainedMongoClient client : retained) {
                ReloadTck.assertRetained(harness, client);
            }

            // the new field is encoded, and decoded by the new query method; the document of generation 1 decodes
            add(harness.context(), "Two", "Ann");
            assertEquals(List.of("Two"), byAuthor(harness.context(), "Ann"));
            assertEquals(List.of("One by null", "Two by Ann"), titles(harness.context()));
            ReloadTck.assertFollowsReload(harness, MongoDataReloadTest::repository);
            retained = null;

            ReloadTck.assertRetiredGenerationsCollected(harness);
        }
    }

    @Test
    void anEntityChangedInPlaceRecreatesTheDataBeansAndKeepsTheConfigurationAndTheRetainedClient() {
        try (ReloadHarness harness = ReloadHarness.inDirectory(project)) {
            database(harness);
            first(harness);
            ApplicationContext context = harness.start();
            add(context, "One");
            List<RetainedMongoClient> retained = retained(context);
            DefaultMongoConfiguration configuration = context.getBean(DefaultMongoConfiguration.class);
            Object registry = context.getBean(RuntimeEntityRegistry.class);
            Object operations = context.getBean(RepositoryOperations.class);
            MongoClient client = context.getBean(MongoClient.class);
            ClassLoader loader = context.getClassLoader();

            // the entity redefined in place, as an in-place reload does
            context.publishEvent(new ClassChangeEvent(this, Set.of(), loader, List.of(new ClassChange(BOOK, ClassChange.Kind.MODIFIED)), ReloadStrategy.RELOAD));

            // the data beans are recreated, and the client bean of the generation, which builds its codecs again
            assertNotSame(registry, context.getBean(RuntimeEntityRegistry.class));
            assertNotSame(operations, context.getBean(RepositoryOperations.class));
            assertNotSame(client, context.getBean(MongoClient.class));
            // the configuration that received the codec registry builder, and the driver client built from it, are kept
            assertSame(configuration, context.getBean(DefaultMongoConfiguration.class));
            assertSameClients(retained, retained(context));

            add(context, "Two");
            assertEquals(List.of("One by null", "Two by null"), titles(context));

            // a classloader retired in place: the data beans and the clients of the generation are recreated, and
            // read the documents. Micronaut MongoDB recreates the configurations with package names then, for the
            // discriminator classes its own codec registry builder caches, and with them the retained clients
            context.publishEvent(new ClassChangeEvent(this, Set.of(loader), loader, List.of(), ReloadStrategy.RELOAD));
            assertEquals(List.of("One by null", "Two by null"), titles(context));
        }
    }

    private static void database(ReloadHarness harness) {
        harness.property("mongodb.uri", mongo.getReplicaSetUrl("dev" + UUID.randomUUID().toString().replace("-", "")));
        harness.property("micronaut.data.mongodb.driver-type", "sync");
    }

    private static void assertSameClients(List<RetainedMongoClient> expected, List<RetainedMongoClient> actual) {
        assertEquals(expected.size(), actual.size());
        for (RetainedMongoClient client : expected) {
            assertTrue(actual.stream().anyMatch(candidate -> candidate == client), "the retained client is kept");
        }
    }

    private static List<RetainedMongoClient> retained(ApplicationContext context) {
        Collection<RetainedMongoClient> clients = context.getBeansOfType(RetainedMongoClient.class);
        return new ArrayList<>(clients);
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
                private String id;
                private String title;

                public String getId() { return id; }
                public void setId(String id) { this.id = id; }
                public String getTitle() { return title; }
                public void setTitle(String title) { this.title = title; }
                public String describe() { return title + " by null"; }
            }
            """);
        harness.source(REPOSITORY, """
            package example;

            import io.micronaut.data.mongodb.annotation.MongoRepository;
            import io.micronaut.data.repository.CrudRepository;

            @MongoRepository
            public interface BookRepository extends CrudRepository<Book, String> {
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

                public String add(String title) {
                    Book book = new Book();
                    book.setTitle(title);
                    return books.save(book).getId();
                }

                public List<String> titles() {
                    List<String> titles = new ArrayList<>();
                    for (Book book : books.findAll()) {
                        titles.add(book.describe());
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
                private String id;
                private String title;
                private String author;

                public String getId() { return id; }
                public void setId(String id) { this.id = id; }
                public String getTitle() { return title; }
                public void setTitle(String title) { this.title = title; }
                public String getAuthor() { return author; }
                public void setAuthor(String author) { this.author = author; }
                public String describe() { return title + " by " + author; }
            }
            """);
        harness.source(REPOSITORY, """
            package example;

            import io.micronaut.data.mongodb.annotation.MongoRepository;
            import io.micronaut.data.repository.CrudRepository;

            import java.util.List;

            @MongoRepository
            public interface BookRepository extends CrudRepository<Book, String> {
                List<Book> findByAuthor(String author);
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

                public String add(String title, String author) {
                    Book book = new Book();
                    book.setTitle(title);
                    book.setAuthor(author);
                    return books.save(book).getId();
                }

                public List<String> byAuthor(String author) {
                    List<String> titles = new ArrayList<>();
                    for (Book book : books.findByAuthor(author)) {
                        titles.add(book.getTitle());
                    }
                    return titles;
                }

                public List<String> titles() {
                    List<String> titles = new ArrayList<>();
                    for (Book book : books.findAll()) {
                        titles.add(book.describe());
                    }
                    titles.sort(null);
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
        add(context, "One");
        assertEquals(List.of("One by null"), titles(context));
    }

    private static void add(ApplicationContext context, String title) {
        invoke(context, "add", new Class<?>[] {String.class}, title);
    }

    private static void add(ApplicationContext context, String title, String author) {
        invoke(context, "add", new Class<?>[] {String.class, String.class}, title, author);
    }

    @SuppressWarnings("unchecked")
    private static List<String> titles(ApplicationContext context) {
        return (List<String>) invoke(context, "titles", new Class<?>[0]);
    }

    @SuppressWarnings("unchecked")
    private static List<String> byAuthor(ApplicationContext context, String author) {
        return (List<String>) invoke(context, "byAuthor", new Class<?>[] {String.class}, author);
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
