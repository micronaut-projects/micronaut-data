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

import io.micronaut.data.model.CursoredPage;
import io.micronaut.data.model.CursoredPageable;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.entities.Person;
import io.micronaut.data.tck.repositories.PersonRepository;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
@SQLiteDBProperties
class SQLiteCursoredPaginationTest {

    @Inject
    SQLitePersonRepository personRepository;

    @Inject
    SQLiteBookRepository bookRepository;

    @BeforeEach
    void setup() {
        bookRepository.deleteAll();
        personRepository.deleteAll();
        personRepository.saveAll(createPeople());
    }

    @AfterEach
    void cleanup() {
        bookRepository.deleteAll();
        personRepository.deleteAll();
    }

    @Test
    void testCursoredPageableListForSorting() {
        for (Object[] args : sortingArguments()) {
            Sort sorting = (Sort) args[0];
            String name1 = (String) args[1];
            String name2 = (String) args[2];
            String name10 = (String) args[3];
            String name19 = (String) args[4];

            CursoredPageable pageable = CursoredPageable.from(10, sorting);
            CursoredPage<Person> page = assertCursored(personRepository.findAll(pageable));

            assertEquals(10, page.getContent().size());
            assertTrue(page.getContent().stream().allMatch(Person.class::isInstance));
            assertEquals(name1, page.getContent().get(0).getName());
            assertEquals(name2, page.getContent().get(1).getName());
            assertEquals(780, page.getTotalSize());
            assertEquals(78, page.getTotalPages());
            assertTrue(page.getCursor(0).isPresent());
            assertTrue(page.getCursor(9).isPresent());
            assertTrue(page.hasNext());

            page = assertCursored(personRepository.findAll(page.nextPageable()));

            assertEquals(10, page.getOffset());
            assertEquals(1, page.getPageNumber());
            assertEquals(name10, page.getContent().get(0).getName());
            assertEquals(name19, page.getContent().get(9).getName());
            assertEquals(10, page.getContent().size());
            assertTrue(page.hasNext());
            assertTrue(page.hasPrevious());

            pageable = page.previousPageable();
            page = assertCursored(personRepository.findAll(pageable));

            assertEquals(0, page.getOffset());
            assertEquals(0, page.getPageNumber());
            assertEquals(name1, page.getContent().get(0).getName());
            assertEquals(10, page.getContent().size());
            assertTrue(page.hasNext());
            assertTrue(page.hasPrevious());
        }
    }

    @Test
    void testPageableListWithRowRemoval() {
        for (Object[] args : rowRemovalArguments()) {
            setup();
            Sort sorting = (Sort) args[0];
            String elem1 = (String) args[1];
            String elem2 = (String) args[2];
            String elem10 = (String) args[3];
            String elem19 = (String) args[4];

            Pageable pageable = Pageable.from(0, 10, sorting);
            CursoredPage<Person> page = personRepository.retrieve(pageable);

            assertEquals(10, page.getContent().size());
            assertEquals(elem1, page.getContent().get(0).getName());
            assertEquals(elem2, page.getContent().get(1).getName());
            assertTrue(page.hasNext());

            personRepository.delete(page.getContent().get(1));
            personRepository.delete(page.getContent().get(9));
            page = personRepository.retrieve(page.nextPageable());

            assertEquals(10, page.getOffset());
            assertEquals(1, page.getPageNumber());
            assertEquals(elem10, page.getContent().get(0).getName());
            assertEquals(elem19, page.getContent().get(9).getName());
            assertEquals(10, page.getContent().size());
            assertTrue(page.hasNext());
            assertTrue(page.hasPrevious());

            pageable = page.previousPageable();
            page = personRepository.retrieve(pageable);

            assertEquals(0, page.getOffset());
            assertEquals(0, page.getPageNumber());
            assertEquals(elem1, page.getContent().get(0).getName());
            assertEquals(8, page.getContent().size());
            assertTrue(page.getCursor(7).isPresent());
            assertTrue(page.getCursor(8).isEmpty());
            assertFalse(page.hasPrevious());
            assertTrue(page.hasNext());
        }
    }

    @Test
    void testPageableListWithRowAddition() {
        for (Object[] args : rowAdditionArguments()) {
            setup();
            Sort sorting = (Sort) args[0];
            String elem1 = (String) args[1];
            String elem2 = (String) args[2];
            String elem3 = (String) args[3];
            String elem10 = (String) args[4];
            String elem19 = (String) args[5];

            CursoredPage<Person> page = personRepository.retrieve(CursoredPageable.from(10, sorting));

            assertEquals(10, page.getContent().size());
            assertEquals(elem1, page.getContent().get(0).getName());
            assertEquals(elem2, page.getContent().get(1).getName());
            assertTrue(page.hasNext());

            personRepository.saveAll(List.of(
                person("AAAAA00"),
                person("AAAAA01"),
                person("ZZZZZ08"),
                person("ZZZZZ07")
            ));
            page = personRepository.retrieve(page.nextPageable());

            assertEquals(10, page.getOffset());
            assertEquals(1, page.getPageNumber());
            assertEquals(elem10, page.getContent().get(0).getName());
            assertEquals(elem19, page.getContent().get(9).getName());
            assertEquals(10, page.getContent().size());
            assertTrue(page.hasNext());
            assertTrue(page.hasPrevious());

            page = personRepository.retrieve(page.previousPageable());

            assertEquals(0, page.getOffset());
            assertEquals(0, page.getPageNumber());
            assertEquals(elem3, page.getContent().get(0).getName());
            assertEquals(10, page.getContent().size());
            assertTrue(page.hasPrevious());

            page = personRepository.retrieve(page.previousPageable());

            assertEquals(0, page.getOffset());
            assertEquals(0, page.getPageNumber());
            assertEquals(elem1, page.getContent().get(0).getName());
            assertEquals(elem2, page.getContent().get(1).getName());
            assertTrue(page.getCursor(1).isPresent());
            assertTrue(page.getCursor(2).isEmpty());
            assertEquals(2, page.getContent().size());
            assertFalse(page.hasPrevious());
        }
    }

    @Test
    void testCursoredPageable() {
        List<Function<Pageable, Page<Person>>> resultFunctions = List.of(
            pageable -> personRepository.findByNameLike("A%", pageable),
            pageable -> personRepository.findAll(PersonRepository.Specifications.nameLike("A%"), (CursoredPageable) pageable)
        );
        for (Function<Pageable, Page<Person>> resultFunction : resultFunctions) {
            CursoredPageable pageable = CursoredPageable.from(10, null);
            Page<Person> page = resultFunction.apply(pageable);
            Page<Person> page2 = personRepository.findPeople("A%", pageable);

            assertEquals(0, page.getOffset());
            assertEquals(0, page.getPageNumber());
            assertEquals(30, page.getTotalSize());
            assertEquals(page.getTotalSize(), page2.getTotalSize());
            List<Long> firstContentIds = ids(page.getContent());
            assertTrue(page.getContent().stream().map(Person::getName).allMatch(name -> name.startsWith("A")));

            page = resultFunction.apply(page.nextPageable());

            assertEquals(10, page.getOffset());
            assertEquals(1, page.getPageNumber());
            assertNotEquals(firstContentIds, ids(page.getContent()));
            assertTrue(page.getContent().stream().map(Person::getName).allMatch(name -> name.startsWith("A")));

            page = resultFunction.apply(page.previousPageable());

            assertEquals(0, page.getOffset());
            assertEquals(0, page.getPageNumber());
            assertEquals(10, page.getContent().size());
            assertEquals(firstContentIds, ids(page.getContent()));
            assertTrue(page.getContent().stream().map(Person::getName).allMatch(name -> name.startsWith("A")));
        }
    }

    @Test
    void testFindWithLeftJoin() {
        List<Book> books = new ArrayList<>();
        bookRepository.saveAll(List.of(
            book("Book 1", 100),
            book("Book 2", 100)
        )).forEach(books::add);

        CursoredPage<Book> page = assertCursored(bookRepository.findByTotalPagesGreaterThan(
            50, CursoredPageable.from(books.size(), null)
        ));

        assertEquals(books.size(), page.getContent().size());
        assertEquals(books.size(), page.getTotalSize());

        CursoredPage<String> pageOfTitles = page.map(Book::getTitle);
        assertTrue(pageOfTitles.hasTotalSize());
        List<String> titles = pageOfTitles.getContent();
        assertTrue(titles.contains("Book 1"));
        assertTrue(titles.contains("Book 2"));

        page = assertCursored(bookRepository.findAll(page.nextPageable().withoutTotal()));
        pageOfTitles = page.map(Book::getTitle);
        assertFalse(pageOfTitles.hasTotalSize());
        titles = pageOfTitles.getContent();

        assertTrue(titles.isEmpty());
    }

    @Test
    void testCursoredPageableWithoutPageSize() {
        CursoredPageable pageable = CursoredPageable.from(Sort.of(Sort.Order.desc("name")));
        CursoredPage<Person> page = personRepository.findAll(PersonRepository.Specifications.nameLike("BBBB%"), pageable);

        assertEquals(0, page.getOffset());
        assertEquals(0, page.getPageNumber());
        assertEquals(30, page.getTotalSize());
        assertFalse(page.getContent().isEmpty());
        assertTrue(page.getContent().stream().allMatch(Person.class::isInstance));

        page = personRepository.findAll(PersonRepository.Specifications.nameLike("BBBB%"), page.nextPageable());

        assertEquals(0, page.getOffset());
        assertEquals(1, page.getPageNumber());
        assertEquals(30, page.getTotalSize());
        assertEquals(0, page.nextPageable().getOffset());
        assertEquals(2, page.nextPageable().getNumber());
        assertTrue(page.getContent().isEmpty());
    }

    private static List<Object[]> sortingArguments() {
        return List.of(
            new Object[]{null, "AAAAA00", "AAAAA01", "BBBBB00", "BBBBB09"},
            new Object[]{Sort.of(Sort.Order.desc("id")), "ZZZZZ09", "ZZZZZ08", "YYYYY09", "YYYYY00"},
            new Object[]{Sort.of(Sort.Order.asc("name")), "AAAAA00", "AAAAA00", "AAAAA03", "AAAAA06"},
            new Object[]{Sort.of(Sort.Order.desc("name")), "ZZZZZ09", "ZZZZZ09", "ZZZZZ06", "ZZZZZ03"},
            new Object[]{Sort.of(Sort.Order.asc("age"), Sort.Order.asc("name")), "AAAAA00", "BBBBB00", "KKKKK00", "TTTTT00"},
            new Object[]{Sort.of(Sort.Order.desc("age"), Sort.Order.asc("name")), "AAAAA09", "BBBBB09", "KKKKK09", "TTTTT09"}
        );
    }

    private static List<Object[]> rowRemovalArguments() {
        return List.of(
            new Object[]{null, "AAAAA00", "AAAAA01", "BBBBB00", "BBBBB09"},
            new Object[]{Sort.of(Sort.Order.desc("id")), "ZZZZZ09", "ZZZZZ08", "YYYYY09", "YYYYY00"},
            new Object[]{Sort.of(Sort.Order.asc("name")), "AAAAA00", "AAAAA00", "AAAAA03", "AAAAA06"},
            new Object[]{Sort.of(Sort.Order.desc("name")), "ZZZZZ09", "ZZZZZ09", "ZZZZZ06", "ZZZZZ03"}
        );
    }

    private static List<Object[]> rowAdditionArguments() {
        return List.of(
            new Object[]{Sort.of(Sort.Order.asc("name")), "AAAAA00", "AAAAA00", "AAAAA00", "AAAAA03", "AAAAA06"},
            new Object[]{Sort.of(Sort.Order.desc("name")), "ZZZZZ09", "ZZZZZ09", "ZZZZZ09", "ZZZZZ06", "ZZZZZ03"}
        );
    }

    private static List<Long> ids(List<Person> people) {
        return people.stream().map(Person::getId).toList();
    }

    private static Person person(String name) {
        Person person = new Person();
        person.setName(name);
        return person;
    }

    private static Book book(String title, int totalPages) {
        Book book = new Book();
        book.setTitle(title);
        book.setTotalPages(totalPages);
        return book;
    }

    private static List<Person> createPeople() {
        List<Person> people = new ArrayList<>();
        for (int group = 0; group < 3; group++) {
            for (char letter = 'A'; letter <= 'Z'; letter++) {
                for (int num = 0; num < 10; num++) {
                    Person person = new Person();
                    person.setName(String.valueOf(letter).repeat(5) + String.format("%02d", num));
                    person.setAge(group * 10 + num + 1);
                    people.add(person);
                }
            }
        }
        return people;
    }

    @SuppressWarnings("unchecked")
    private static <T> CursoredPage<T> assertCursored(Page<T> page) {
        return assertInstanceOf(CursoredPage.class, page);
    }
}
