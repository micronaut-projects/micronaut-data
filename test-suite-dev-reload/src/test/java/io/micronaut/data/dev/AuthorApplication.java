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
 * An application whose books refer to their authors through a foreign key, in two versions: the second adds a
 * column to the authors.
 */
final class AuthorApplication {

    private static final String AUTHOR = "example.Author";
    private static final String BOOK = "example.Book";
    private static final String SHELF = "example.Shelf";

    private AuthorApplication() {
    }

    static void first(ReloadHarness harness) {
        harness.source(AUTHOR, author(""));
        harness.source(BOOK, """
            package example;

            import io.micronaut.data.annotation.GeneratedValue;
            import io.micronaut.data.annotation.Id;
            import io.micronaut.data.annotation.MappedEntity;
            import io.micronaut.data.annotation.Relation;

            @MappedEntity
            public class Book {
                @Id
                @GeneratedValue
                private Long id;
                private String title;
                @Relation(Relation.Kind.MANY_TO_ONE)
                private Author author;

                public Long getId() { return id; }
                public void setId(Long id) { this.id = id; }
                public String getTitle() { return title; }
                public void setTitle(String title) { this.title = title; }
                public Author getAuthor() { return author; }
                public void setAuthor(Author author) { this.author = author; }
            }
            """);
        harness.source("example.AuthorRepository", """
            package example;

            @io.micronaut.data.jdbc.annotation.JdbcRepository(dialect = io.micronaut.data.model.query.builder.sql.Dialect.H2)
            public interface AuthorRepository extends io.micronaut.data.repository.CrudRepository<Author, Long> {
            }
            """);
        harness.source("example.BookRepository", """
            package example;

            @io.micronaut.data.jdbc.annotation.JdbcRepository(dialect = io.micronaut.data.model.query.builder.sql.Dialect.H2)
            public interface BookRepository extends io.micronaut.data.repository.CrudRepository<Book, Long> {
            }
            """);
        harness.source(SHELF, shelf(""));
    }

    static void second(ReloadHarness harness) {
        harness.source(AUTHOR, author("""
                private int born;

                public int getBorn() { return born; }
                public void setBorn(int born) { this.born = born; }
            """));
        harness.source(SHELF, shelf("author.setBorn(1950);"));
    }

    static void useFirstGeneration(ReloadHarness harness) {
        ApplicationContext context = harness.context();
        shelve(context, "Ann", "One");
        assertEquals(List.of("Ann: One"), shelved(context));
    }

    static void useSecondGeneration(ReloadHarness harness) {
        ApplicationContext context = harness.context();
        shelve(context, "Bob", "Two");
        assertEquals(List.of("Bob: Two"), shelved(context));
    }

    private static String author(String more) {
        return """
            package example;

            import io.micronaut.data.annotation.GeneratedValue;
            import io.micronaut.data.annotation.Id;
            import io.micronaut.data.annotation.MappedEntity;

            @MappedEntity
            public class Author {
                @Id
                @GeneratedValue
                private Long id;
                private String name;
            %s
                public Long getId() { return id; }
                public void setId(Long id) { this.id = id; }
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
            }
            """.formatted(more);
    }

    private static String shelf(String moreAuthor) {
        return """
            package example;

            import java.util.ArrayList;
            import java.util.List;

            @jakarta.inject.Singleton
            public class Shelf {
                private final AuthorRepository authors;
                private final BookRepository books;

                public Shelf(AuthorRepository authors, BookRepository books) {
                    this.authors = authors;
                    this.books = books;
                }

                public void shelve(String name, String title) {
                    Author author = new Author();
                    author.setName(name);
                    %s
                    Book book = new Book();
                    book.setTitle(title);
                    book.setAuthor(authors.save(author));
                    books.save(book);
                }

                public List<String> shelved() {
                    List<String> shelved = new ArrayList<>();
                    for (Book book : books.findAll()) {
                        shelved.add(authors.findById(book.getAuthor().getId()).orElseThrow().getName() + ": " + book.getTitle());
                    }
                    return shelved;
                }
            }
            """.formatted(moreAuthor);
    }

    private static void shelve(ApplicationContext context, String name, String title) {
        invoke(context, "shelve", new Class<?>[] {String.class, String.class}, name, title);
    }

    @SuppressWarnings("unchecked")
    private static List<String> shelved(ApplicationContext context) {
        return (List<String>) invoke(context, "shelved", new Class<?>[0]);
    }

    private static Object invoke(ApplicationContext context, String method, Class<?>[] parameterTypes, Object... args) {
        Class<?> shelf = DevApplication.type(context, SHELF);
        try {
            return shelf.getMethod(method, parameterTypes).invoke(context.getBean(shelf), args);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot invoke " + method, e);
        }
    }
}
