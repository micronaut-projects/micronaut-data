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

import io.micronaut.context.ApplicationContext;
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.tck.entities.AuthorBooksDto;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.entities.BookDto;
import io.micronaut.data.tck.entities.Person;
import io.micronaut.data.tck.entities.Student;
import io.micronaut.data.tck.entities.embedded.BookEntity;
import io.micronaut.data.tck.entities.embedded.BookState;
import io.micronaut.data.tck.entities.embedded.ResourceEntity;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static io.micronaut.data.tck.repositories.PersonRepository.Specifications.findNameSubqueryEq;
import static io.micronaut.data.tck.repositories.PersonRepository.Specifications.findNameSubqueryIn;
import static io.micronaut.data.tck.repositories.PersonRepository.Specifications.nameEqualsCaseInsensitive;
import static io.micronaut.data.tck.repositories.PersonRepository.Specifications.subqueriesWithJoinReferencingOuter;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractSQLiteRepositoryBehaviorTest implements SQLiteTestingPropertyProvider {

    private ApplicationContext context;

    @BeforeAll
    void setupContext() {
        context = ApplicationContext.run(new HashMap<>(getProperties()));
    }

    @AfterEach
    void cleanup() {
        cleanupData();
    }

    @AfterAll
    void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    @Override
    public Map<String, String> getProperties() {
        return SQLiteTestingPropertyProvider.super.getProperties();
    }

    @Test
    void testSubqueryWithJoin() {
        saveSampleBooks();

        List<Book> books = context.getBean(SQLiteBookRepository.class).findAll(subqueriesWithJoinReferencingOuter());

        assertEquals(6, books.size());
    }

    @Test
    void testSubqueryIn() {
        savePersons(List.of("Jeff", "James"));

        var person = context.getBean(SQLitePersonRepository.class).findOne(findNameSubqueryIn("James"));

        assertNotNull(person);
    }

    @Test
    void testSubqueryEq() {
        savePersons(List.of("Jeff", "James"));

        var person = context.getBean(SQLitePersonRepository.class).findOne(findNameSubqueryEq("James"));

        assertNotNull(person);
    }

    @Test
    void testCriteriaLowerSelect() {
        savePersons(List.of("Jeff", "James"));

        var person = context.getBean(SQLitePersonRepository.class).findOne(nameEqualsCaseInsensitive("james"));

        assertTrue(person.isPresent());
    }

    @Test
    void testManualJoiningOnManyEndedAssociation() {
        saveSampleBooks();

        var author = context.getBean(SQLiteBookService.class).findByName("Stephen King");

        assertNotNull(author);
        assertEquals("Stephen King", author.getName());
        assertEquals(2, author.getBooks().size());
        assertTrue(author.getBooks().stream().anyMatch(book -> "The Stand".equals(book.getTitle())));
        assertTrue(author.getBooks().stream().anyMatch(book -> "Pet Cemetery".equals(book.getTitle())));
    }

    @Test
    void testSqlMappingFunction() {
        saveSampleBooks();

        var authorRepository = context.getBean(SQLiteAuthorRepository.class);

        var book = authorRepository.testReadSingleProperty("The Stand", 700);
        assertNotNull(book);
        assertEquals("Stephen King", book.getAuthor().getName());

        book = authorRepository.testReadAssociatedEntity("The Stand", 700);
        assertNotNull(book);
        assertEquals("Stephen King", book.getAuthor().getName());
        assertNotNull(book.getAuthor().getId());

        book = authorRepository.testReadDTO("The Stand", 700);
        assertNotNull(book);
        assertEquals("Stephen King", book.getAuthor().getName());
    }

    @Test
    void findByEmbeddedEntityField() {
        var bookEntityRepository = context.getBean(SQLiteBookEntityRepository.class);
        BookEntity bookEntity = new BookEntity(1L, new ResourceEntity<>("1984", BookState.BORROWED));

        bookEntityRepository.insert(bookEntity);
        var result = bookEntityRepository.findAllByResourceState(BookState.BORROWED);

        assertFalse(result.isEmpty());
        bookEntityRepository.deleteAll();
    }

    @Test
    void testJoinPaginationXxx() {
        Student denis = new Student("Denis");
        Student josh = new Student("Josh");
        Student kevin = new Student("Kevin");
        Book book1 = new Book();
        book1.setTitle("The Stand");
        book1.setStudents(new HashSet<>(List.of(denis, josh)));
        Book book2 = new Book();
        book2.setTitle("Pet Cemetery");
        book2.setStudents(new HashSet<>(List.of(kevin)));
        Book book3 = new Book();
        book3.setTitle("Along Came a Spider");
        book3.setStudents(new HashSet<>(List.of(kevin, josh)));
        var bookRepository = context.getBean(SQLiteBookRepository.class);
        bookRepository.save(book1);
        bookRepository.save(book2);
        bookRepository.save(book3);
        List<String> names = List.of(denis.getName(), josh.getName());

        io.micronaut.data.model.Page<Book> page = bookRepository.findAllByStudentsNameIn(
            names,
            Pageable.from(0, 10, Sort.of(Sort.Order.asc("title")))
        );

        assertEquals(page.getTotalSize(), page.getContent().size());
        assertEquals(2, page.getTotalSize());
        assertEquals(List.of("Along Came a Spider", "The Stand"), sortedTitles(page.getContent()));
        assertEquals(List.of("Josh", "Kevin"), sortedStudentNames(page.getContent().get(0)));
        assertEquals(List.of("Denis", "Josh"), sortedStudentNames(page.getContent().get(1)));

        Pageable pageable = Pageable.from(0, 1, Sort.of(Sort.Order.asc("title")));
        page = bookRepository.findAllByStudentsNameIn(names, pageable);

        assertEquals(2, page.getTotalSize());
        assertEquals(1, page.getContent().size());
        assertEquals("Along Came a Spider", page.getContent().get(0).getTitle());
        assertEquals(List.of("Josh", "Kevin"), sortedStudentNames(page.getContent().get(0)));

        pageable = pageable.next();
        page = bookRepository.findAllByStudentsNameIn(names, pageable);

        assertEquals(2, page.getTotalSize());
        assertEquals(1, page.getContent().size());
        assertEquals("The Stand", page.getContent().get(0).getTitle());
        assertEquals(List.of("Denis", "Josh"), sortedStudentNames(page.getContent().get(0)));

        pageable = pageable.next();
        page = bookRepository.findAllByStudentsNameIn(names, pageable);

        assertEquals(2, page.getTotalSize());
        assertEquals(0, page.getContent().size());

        pageable = pageable.previous();
        page = bookRepository.findAllByStudentsNameIn(names, pageable);

        assertEquals(2, page.getTotalSize());
        assertEquals(1, page.getContent().size());
        assertEquals("The Stand", page.getContent().get(0).getTitle());
        assertEquals(List.of("Denis", "Josh"), sortedStudentNames(page.getContent().get(0)));
    }

    private void savePersons(List<String> names) {
        List<Person> people = new ArrayList<>();
        for (String name : names) {
            Person person = new Person();
            person.setName(name);
            people.add(person);
        }
        context.getBean(SQLitePersonRepository.class).saveAll(people);
    }

    private void saveSampleBooks() {
        context.getBean(SQLiteBookRepository.class).saveAuthorBooks(List.of(
            new AuthorBooksDto("Stephen King", List.of(
                new BookDto("The Stand", 1000),
                new BookDto("Pet Cemetery", 400)
            )),
            new AuthorBooksDto("James Patterson", List.of(
                new BookDto("Along Came a Spider", 300),
                new BookDto("Double Cross", 300)
            )),
            new AuthorBooksDto("Don Winslow", List.of(
                new BookDto("The Power of the Dog", 600),
                new BookDto("The Border", 700)
            ))
        ));
    }

    private void cleanupData() {
        var studentRepository = context.getBean(SQLiteStudentRepository.class);
        var bookRepository = context.getBean(SQLiteBookRepository.class);
        var authorRepository = context.getBean(SQLiteAuthorRepository.class);
        studentRepository.deleteAll();
        try (Connection connection = dataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM \"book_student\"");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to clean up book_student rows", e);
        }
        bookRepository.deleteAll();
        authorRepository.deleteAll();
        context.getBean(SQLitePersonRepository.class).deleteAll();
        context.getBean(SQLiteIntervalRepository.class).deleteAll();
    }

    private DataSource dataSource() {
        DataSource dataSource = context.getBean(DataSource.class, Qualifiers.byName("default"));
        return DelegatingDataSource.unwrapDataSource(dataSource);
    }

    private List<String> sortedTitles(List<Book> books) {
        return books.stream()
            .map(Book::getTitle)
            .sorted()
            .toList();
    }

    private List<String> sortedStudentNames(Book book) {
        List<String> names = new ArrayList<>();
        for (Student student : book.getStudents()) {
            names.add(student.getName());
        }
        names.sort(Comparator.naturalOrder());
        return names;
    }
}
