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

import io.micronaut.context.ApplicationContext;
import io.micronaut.dev.tck.ReloadHarness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The application under test: an entity, its JDBC repository and a service over them, in two versions. The
 * second adds a column to the entity and a query method to the repository. The test reaches them by reflection,
 * since they are compiled and loaded by the development runtime.
 */
final class DevApplication {

    static final String BOOK = "example.Book";
    static final String REPOSITORY = "example.BookRepository";
    static final String LIBRARY = "example.Library";

    private DevApplication() {
    }

    static void first(ReloadHarness harness) {
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

            import io.micronaut.data.jdbc.annotation.JdbcRepository;
            import io.micronaut.data.model.query.builder.sql.Dialect;
            import io.micronaut.data.repository.CrudRepository;

            @JdbcRepository(dialect = Dialect.H2)
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
                    return titles;
                }
            }
            """);
    }

    /**
     * The second version of the repository and the service only: a query method by title, the same entity.
     *
     * @param harness The harness
     */
    static void secondRepositoryOnly(ReloadHarness harness) {
        harness.source(REPOSITORY, """
            package example;

            import io.micronaut.data.jdbc.annotation.JdbcRepository;
            import io.micronaut.data.model.query.builder.sql.Dialect;
            import io.micronaut.data.repository.CrudRepository;

            import java.util.List;

            @JdbcRepository(dialect = Dialect.H2)
            public interface BookRepository extends CrudRepository<Book, Long> {
                List<Book> findByTitle(String title);
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

                public List<String> titles() {
                    List<String> titles = new ArrayList<>();
                    for (Book book : books.findAll()) {
                        titles.add(book.getTitle());
                    }
                    return titles;
                }

                public List<String> titled(String title) {
                    List<String> titles = new ArrayList<>();
                    for (Book book : books.findByTitle(title)) {
                        titles.add(book.getTitle());
                    }
                    return titles;
                }
            }
            """);
    }

    static void second(ReloadHarness harness) {
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

            import io.micronaut.data.jdbc.annotation.JdbcRepository;
            import io.micronaut.data.model.query.builder.sql.Dialect;
            import io.micronaut.data.repository.CrudRepository;

            import java.util.List;

            @JdbcRepository(dialect = Dialect.H2)
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
     * Saves a book and reads it back through the first version.
     *
     * @param harness The harness, on generation one
     */
    static void useFirstGeneration(ReloadHarness harness) {
        ApplicationContext context = harness.context();
        assertEquals(1L, add(context, "One"));
        assertEquals(List.of("One"), titles(context));
    }

    /**
     * Saves books with the new column, and queries them with the new method of the second version.
     *
     * @param harness The harness, on generation two
     */
    static void useSecondGeneration(ReloadHarness harness) {
        ApplicationContext context = harness.context();
        add(context, "Two", 200);
        add(context, "Three", 300);
        assertEquals(List.of("Three"), longerThan(context, 250));
    }

    static long add(ApplicationContext context, String title) {
        return (Long) invoke(context, "add", new Class<?>[] {String.class}, title);
    }

    static long add(ApplicationContext context, String title, int pages) {
        return (Long) invoke(context, "add", new Class<?>[] {String.class, int.class}, title, pages);
    }

    @SuppressWarnings("unchecked")
    static List<String> titles(ApplicationContext context) {
        return (List<String>) invoke(context, "titles", new Class<?>[0]);
    }

    @SuppressWarnings("unchecked")
    static List<String> titled(ApplicationContext context, String title) {
        return (List<String>) invoke(context, "titled", new Class<?>[] {String.class}, title);
    }

    @SuppressWarnings("unchecked")
    static List<String> longerThan(ApplicationContext context, int pages) {
        return (List<String>) invoke(context, "longerThan", new Class<?>[] {int.class}, pages);
    }

    static Object repository(ApplicationContext context) {
        return context.getBean(type(context, REPOSITORY));
    }

    static Class<?> type(ApplicationContext context, String className) {
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
