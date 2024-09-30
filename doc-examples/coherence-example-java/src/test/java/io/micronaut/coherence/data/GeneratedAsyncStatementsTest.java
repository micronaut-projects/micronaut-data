/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.coherence.data;

import com.tangosol.util.UUID;
import io.micronaut.coherence.data.model.Author;
import io.micronaut.coherence.data.model.Book;
import io.micronaut.coherence.data.repositories.AsyncBookRepository;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@MicronautTest(propertySources = {"classpath:sessions.yaml"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class GeneratedAsyncStatementsTest extends AbstractDataTest {

    /**
     * A {@code repository} for validating generated queries.
     */
    @Inject
    protected AsyncBookRepository repo;

    // ----- test methods ---------------------------------------------------

    /**
     * Validate it is possible to query books by id.
     */
    @Test
    public void shouldGetBooksById() {
        for (Book b : books) {
            repo.findById(b.getUuid())
                .thenAccept(book1 -> Assertions.assertEquals(b, book1)).join();
        }
    }

    /**
     * Validate it is possible to obtain all books associated with a specific {@link Author author}.
     */
    @Test
    public void shouldGetBooksByAuthor() {
        repo.findByAuthor(FRANK_HERBERT)
            .thenAccept(books1 -> Assertions.assertTrue(books1.containsAll(
                books.stream().filter(book -> book.getAuthor().equals(FRANK_HERBERT)).toList()))).join();
    }

    /**
     * Validate it is possible to find books with pages greater or equal to some value.
     */
    @Test
    public void shouldGetBooksWithPagesGreaterOrEqualTo() {
        repo.findByPagesGreaterThanEquals(468)
            .thenAccept(books1 -> Assertions.assertTrue(books1.containsAll(
                books.stream().filter(book -> book.getPages() >= 468).toList())))
            .join();

    }

    /**
     * Validate it is possible to find books with pages less or equal to some value.
     */
    @Test
    public void shouldGetBooksWithPagesLessOrEqualTo() {
        repo.findByPagesLessThanEquals(677)
            .thenAccept(books1 -> Assertions.assertTrue(books1.containsAll(
                books.stream().filter(book -> book.getPages() <= 677).toList())))
            .join();
    }

    /**
     * Validate it is possible to find books using {@code like}.
     */
    @Test
    public void shouldGetBooksWithTitleLike() {
        repo.findByTitleLike("%Dune%")
            .thenAccept(books1 -> Assertions.assertTrue(books1.containsAll(
                books.stream().filter(book -> book.getTitle().contains("Dune")).toList())))
            .join();
    }

    /**
     * Validate returns {@code true} for {@link Book books} were authored by a known {@link Author author}.
     */
    @Test
    public void shouldReturnTrueForValidAuthor() {
        repo.existsByAuthor(FRANK_HERBERT).thenAccept(aBoolean -> Assertions.assertTrue(aBoolean)).join();
    }

    /**
     * Validate returns {@code false} for an {@link Author author}.
     */
    @Test
    public void shouldReturnFalseForInvalidAuthor() {
        repo.existsByAuthor(STEPHEN_KING)
            .thenAccept(aBoolean -> Assertions.assertFalse(aBoolean))
            .join();
    }

    /**
     * Validate the expected result is returned when querying for {@link Book books} {@code before} a specific year.
     */
    @Test
    public void shouldReturnExpectedResultsUsingBefore() {
        repo.findByPublicationYearBefore(1980)
            .thenAccept(books1 -> Assertions.assertTrue(books1.containsAll(
                books.stream().filter(book -> book.getPublicationYear() < 1980).toList())))
            .join();
    }

    /**
     * Validate the expected result is returned when querying for {@link Book books} {@code after} a specific year.
     */
    @Test
    public void shouldReturnExpectedResultsUsingAfter() {
        repo.findByPublicationYearAfter(1980)
            .thenAccept(books1 -> Assertions.assertTrue(books1.containsAll(
                books.stream().filter(book -> book.getPublicationYear() > 1980).toList())))
            .join();
    }

    /**
     * Validate the expected result is returned when searching by a title containing the given string.
     */
    @Test
    public void shouldFindBooksUsingContains() {
        repo.findByTitleContains("Dune")
            .thenAccept(books1 -> Assertions.assertTrue(books1.containsAll(
                books.stream().filter(book -> book.getTitle().contains("Dune")).toList())))
            .join();
    }

    /**
     * Validate the expected result is returned when searching for books with pages numbered greater than a
     * given value.
     */
    @Test
    void shouldFindBooksWithPagesGreaterThan() {
        repo.findByPagesGreaterThan(468)
            .thenAccept(books1 -> Assertions.assertTrue(books1.containsAll(
                books.stream().filter(book -> book.getPages() > 468).toList())))
            .join();
    }

    /**
     * Validate the expected result is returned when searching for books with pages numbered less than a
     * given value.
     */
    @Test
    void shouldFindBooksWithPagesLessThan() {
        repo.findByPagesLessThan(677)
            .thenAccept(books1 -> Assertions.assertTrue(books1.containsAll(
                books.stream().filter(book -> book.getPages() < 677).toList())))
            .join();
    }

    /**
     * Validate the expected results are returned when searching for titles starting with a given string.
     */
    @Test
    void shouldFindByTitleStartingWith() {
        repo.findByTitleStartingWith("Du")
            .thenAccept(books1 -> Assertions.assertTrue(books1.containsAll(
                books.stream().filter(book -> book.getTitle().startsWith("Du")).toList())))
            .join();
    }

    /**
     * Validate the expected results are returned when searching for titles ending with a given string.
     */
    @Test
    void shouldFindByTitleEndingWith() {
        repo.findByTitleEndingWith("Wind")
            .thenAccept(books1 -> Assertions.assertTrue(books1.containsAll(
                books.stream().filter(book -> book.getTitle().endsWith("Wind")).toList())))
            .join();
    }

    /**
     * Validate the expected results are returned when searching for a list of titles.
     */
    @Test
    void shouldFindByTitleIn() {
        List<String> titles = new ArrayList<>();
        titles.add("Dune");
        titles.add("The Name of the Wind");

        repo.findByTitleIn(titles)
            .thenAccept(books1 -> Assertions.assertTrue(books1.containsAll(
                books.stream().filter(book -> book.getTitle().equals("Dune")
                    || book.getTitle().equals("The Name of the Wind")).toList())))
            .join();
    }

    /**
     * Validate the expected results are returned when searching for books published between a given range.
     */
    @Test
    void shouldFindBetweenPublicationYears() {
        repo.findByPublicationYearBetween(1960, 2000)
            .thenAccept(books1 -> Assertions.assertTrue(books1.containsAll(
                books.stream().filter(book -> book.getPublicationYear() > 1960
                    && book.getPublicationYear() < 2000).toList())))
            .join();
    }

    /**
     * Validate the expected results when searching for null authors.
     */
    @Test
    void shouldReturnEmptyListForNullAuthors() {
        repo.findByAuthorIsNull()
            .thenAccept(books1 -> Assertions.assertTrue(books1.containsAll(
                books.stream().filter(book -> book.getAuthor() == null).toList())))
            .join();
    }

    /**
     * Validate the expected results when searching for non-null authors.
     */
    @Test
    void shouldReturnListForNonNullAuthors() {
        repo.findByAuthorIsNotNull()
            .thenAccept(books1 -> Assertions.assertTrue(books1.containsAll(
                books.stream().filter(book -> book.getAuthor() != null).toList())))
            .join();
    }

    /**
     * Validate the expected number of titles with pages greater than input value.
     */
    @Test
    void shouldReturnCountOfTitlesWithPagesGreaterThan() {
        repo.countTitleByPagesGreaterThan(400)
            .thenAccept(aLong -> Assertions.assertEquals(books.stream().filter(book -> book.getPages() > 400).count(), aLong))
            .join();
    }

    /**
     * Validate the expected number of distinct titles with pages greater than input value.
     */
    @Test
    void shouldReturnCountDistinctOfTitlesWithPagesGreaterThan() {
        repo.countDistinctTitleByPagesGreaterThan(400)
            .thenAccept(aLong -> Assertions.assertEquals(books.stream().filter(book -> book.getPages() > 400).count(), aLong))
            .join();
    }

    /**
     * Validate the expected results are returned when searching for a list of titles
     * with pages greater than input value.
     */
    @Test
    void shouldReturnListOfDistinctTitlesWithPagesGreaterThan() {
        repo.findDistinctTitleByPagesGreaterThan(400)
            .thenAccept(books1 -> Assertions.assertTrue(books1.containsAll(
                books.stream()
                    .filter(book -> book.getPages() > 400)
                    .map(Book::getTitle)
                    .distinct().toList())))
            .join();
    }

    /**
     * Validate the expected value is returned when getting max pages by author.
     */
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Test
    void shouldReturnMaxPagesByAuthor() {
        repo.findMaxPagesByAuthor(FRANK_HERBERT)
            .thenAccept(aLong -> Assertions.assertEquals(
                books.stream()
                    .filter(book -> book.getAuthor().equals(FRANK_HERBERT))
                    .map(Book::getPages)
                    .max(Comparator.comparing(Long::valueOf)).get().longValue(), aLong))
            .join();
    }

    /**
     * Validate the expected value is returned when getting min pages by author.
     */
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Test
    void shouldReturnMinPagesByAuthor() {
        repo.findMinPagesByAuthor(FRANK_HERBERT)
            .thenAccept(aLong -> Assertions.assertEquals(
                books.stream()
                    .filter(book -> book.getAuthor().equals(FRANK_HERBERT))
                    .map(Book::getPages)
                    .min(Comparator.comparing(Long::valueOf)).get().longValue(), aLong))
            .join();
    }

    /**
     * Validate the expected value is returned when getting sum pages by author.
     */
    @Test
    void shouldReturnSumPagesByAuthor() {
        repo.findSumPagesByAuthor(FRANK_HERBERT)
            .thenAccept(aLong -> Assertions.assertEquals(
                books.stream()
                    .filter(book -> book.getAuthor().equals(FRANK_HERBERT))
                    .map(Book::getPages)
                    .reduce(0, Integer::sum).longValue(), aLong))
            .join();
    }

    /**
     * Validate the expected value is returned when getting avg pages by author.
     */
    @Test
    void shouldReturnAvgPagesByAuthor() {
        repo.findAvgPagesByAuthor(FRANK_HERBERT)
            .thenAccept(aLong -> Assertions.assertEquals(
                books.stream()
                    .filter(book -> book.getAuthor().equals(FRANK_HERBERT))
                    .collect(Collectors.averagingInt(Book::getPages)).longValue(), aLong))
            .join();
    }

    /**
     * Validate batch updates work as expected.
     */
    @Test
    void shouldSupportBatchUpdates() {
        repo.updateByTitleStartingWith("Du", 700)
            .thenAcceptAsync(unused -> Assertions.assertEquals(700, repo.findById(DUNE.getUuid()).join().getPages()))
            .thenAcceptAsync(unused -> Assertions.assertEquals(700, repo.findById(DUNE_MESSIAH.getUuid()).join().getPages()))
            .join();
    }

    /**
     * Validate single update with existing value returns the expected value and updates
     * the book.
     */
    @Test
    void shouldSupportSingleUpdates() {
        repo.update(DUNE.getUuid(), 999)
            .thenAccept(integer -> Assertions.assertEquals(1, integer))
            .thenAcceptAsync(unused -> Assertions.assertEquals(999, repo.findById(DUNE.getUuid()).join().getPages()))
            .join();
    }

    /**
     * Validate expected return value when the no entity matches.
     */
    @Test
    void shouldSupportSingleUpdatesNoMatch() {
        repo.update(new UUID(), 999)
            .thenAccept(integer -> Assertions.assertEquals(0, integer))
            .join();
    }

    /**
     * Validate batch deletes work as expected.
     */
    @Test
    void shouldSupportBatchDeletes() {
        repo.deleteByTitleStartingWith("Du")
            .thenAcceptAsync(unused -> Assertions.assertEquals(2L, repo.count().join()))
            .thenAcceptAsync(unused -> Assertions.assertFalse(repo.existsById(DUNE.getUuid()).join()))
            .thenAcceptAsync(unused -> Assertions.assertFalse(repo.existsById(DUNE_MESSIAH.getUuid()).join()));
    }

    /**
     * Validate bulk saves work as expected.
     */
    @Test
    void shouldSupportBulksSaves() {
        Set<Book> setNewBooks = new HashSet<>();
        setNewBooks.add(new Book("Children of Dune", 444, FRANK_HERBERT, new GregorianCalendar(1976,
            Calendar.APRIL, 6, 0, 0)));
        setNewBooks.add(new Book("God Emperor of Dune", 496, FRANK_HERBERT, new GregorianCalendar(1981,
            Calendar.FEBRUARY, 6, 0, 0)));
        repo.saveBooks(setNewBooks)
            .thenAcceptAsync(unused -> Assertions.assertTrue(repo.findByTitleIn(
                    Arrays.asList("Children of Dune", "God Emperor of Dune")).join().
                containsAll(setNewBooks))).join();
    }
}
