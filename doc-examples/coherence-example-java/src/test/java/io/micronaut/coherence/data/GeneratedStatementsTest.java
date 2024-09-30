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
import io.micronaut.coherence.data.repositories.BookRepository;
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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@MicronautTest(propertySources = {"classpath:sessions.yaml"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class GeneratedStatementsTest extends AbstractDataTest {
    /**
     * A {@code repository} for validating generated queries.
     */
    @Inject
    protected BookRepository repo;

    // ----- test methods ---------------------------------------------------

    /**
     * Validate it is possible to query books by id.
     */
    @Test
    public void shouldGetBooksById() {
        for (Book b : books) {
            Optional<Book> book = repo.findById(b.getUuid());
            Assertions.assertTrue(book.isPresent());
            Assertions.assertEquals(b, book.get());
        }
    }

    /**
     * Validate it is possible to obtain all books associated with a specific {@link Author author}.
     */
    @Test
    public void shouldGetBooksByAuthor() {
        Assertions.assertTrue(repo.findByAuthor(FRANK_HERBERT).containsAll(
            books.stream().filter(book -> book.getAuthor().equals(FRANK_HERBERT)).toList()));
    }

    /**
     * Validate it is possible to find books with pages greater or equal to some value.
     */
    @Test
    public void shouldGetBooksWithPagesGreaterOrEqualTo() {
        Assertions.assertTrue(repo.findByPagesGreaterThanEquals(468).containsAll(
            books.stream().filter(book -> book.getPages() >= 468).toList()));
    }

    /**
     * Validate it is possible to find books with pages less or equal to some value.
     */
    @Test
    public void shouldGetBooksWithPagesLessOrEqualTo() {
        Assertions.assertTrue(repo.findByPagesLessThanEquals(677).containsAll(
            books.stream().filter(book -> book.getPages() <= 677).toList()));
    }

    /**
     * Validate it is possible to find books using {@code like}.
     */
    @Test
    public void shouldGetBooksWithTitleLike() {
        Assertions.assertTrue(repo.findByTitleLike("%Dune%").containsAll(
            books.stream().filter(book -> book.getTitle().contains("Dune")).toList()));
    }

    /**
     * Validate returns {@code true} for {@link Book books} were authored by a known {@link Author author}.
     */
    @Test
    public void shouldReturnTrueForValidAuthor() {
        Assertions.assertTrue(repo.existsByAuthor(FRANK_HERBERT));
    }

    /**
     * Validate returns {@code false} for an {@link Author author}.
     */
    @Test
    public void shouldReturnFalseForInvalidAuthor() {
        Assertions.assertFalse(repo.existsByAuthor(STEPHEN_KING));
    }

    /**
     * Validate the expected result is returned when querying for {@link Book books} {@code before} a specific year.
     */
    @Test
    public void shouldReturnExpectedResultsUsingBefore() {
        Assertions.assertTrue(repo.findByPublicationYearBefore(1980).containsAll(
            books.stream().filter(book -> book.getPublicationYear() < 1980).toList()));
    }

    /**
     * Validate the expected result is returned when querying for {@link Book books} {@code after} a specific year.
     */
    @Test
    public void shouldReturnExpectedResultsUsingAfter() {
        Assertions.assertTrue(repo.findByPublicationYearAfter(1980).containsAll(
            books.stream().filter(book -> book.getPublicationYear() > 1980).toList()));
    }

    /**
     * Validate the expected result is returned when searching by a title containing the given string.
     */
    @Test
    public void shouldFindBooksUsingContains() {
        Assertions.assertTrue(repo.findByTitleContains("Dune").containsAll(
            books.stream().filter(book -> book.getTitle().contains("Dune")).toList()));
    }

    /**
     * Validate the expected result is returned when searching for books with pages numbered greater than a
     * given value.
     */
    @Test
    void shouldFindBooksWithPagesGreaterThan() {
        Assertions.assertTrue(repo.findByPagesGreaterThan(468).containsAll(
            books.stream().filter(book -> book.getPages() > 468).toList()));
    }

    /**
     * Validate the expected result is returned when searching for books with pages numbered less than a
     * given value.
     */
    @Test
    void shouldFindBooksWithPagesLessThan() {
        Assertions.assertTrue(repo.findByPagesLessThan(677).containsAll(
            books.stream().filter(book -> book.getPages() < 677).toList()));
    }

    /**
     * Validate the expected results are returned when searching for titles starting with a given string.
     */
    @Test
    void shouldFindByTitleStartingWith() {
        Assertions.assertTrue(repo.findByTitleStartingWith("Du").containsAll(
            books.stream().filter(book -> book.getTitle().startsWith("Du")).toList()));
    }

    /**
     * Validate the expected results are returned when searching for titles ending with a given string.
     */
    @Test
    void shouldFindByTitleEndingWith() {
        Assertions.assertTrue(repo.findByTitleEndingWith("Wind").containsAll(
            books.stream().filter(book -> book.getTitle().endsWith("Wind")).toList()));
    }

    /**
     * Validate the expected results are returned when searching for a list of titles.
     */
    @Test
    void shouldFindByTitleIn() {
        List<String> titles = new ArrayList<>();
        titles.add("Dune");
        titles.add("The Name of the Wind");

        Assertions.assertTrue(repo.findByTitleIn(titles).containsAll(
            books.stream().filter(book -> book.getTitle().equals("Dune")
                || book.getTitle().equals("The Name of the Wind")).toList()));
    }

    /**
     * Validate the expected results are returned when searching for books published between a given range.
     */
    @Test
    void shouldFindBetweenPublicationYears() {
        Assertions.assertTrue(repo.findByPublicationYearBetween(1960, 2000).containsAll(
            books.stream().filter(book -> book.getPublicationYear() > 1960
                && book.getPublicationYear() < 2000).toList()));
    }

    /**
     * Validate the expected results when searching for null authors.
     */
    @Test
    void shouldReturnEmptyListForNullAuthors() {
        Assertions.assertTrue(repo.findByAuthorIsNull().containsAll(
            books.stream().filter(book -> book.getAuthor() == null).toList()));
    }

    /**
     * Validate the expected results when searching for non-null authors.
     */
    @Test
    void shouldReturnListForNonNullAuthors() {
        Assertions.assertTrue(repo.findByAuthorIsNotNull().containsAll(
            books.stream().filter(book -> book.getAuthor() != null).toList()));
    }

    /**
     * Validate the expected number of titles with pages greater than input value.
     */
    @Test
    void shouldReturnCountOfTitlesWithPagesGreaterThan() {
        Assertions.assertEquals(books.stream().filter(book -> book.getPages() > 400).count(),
            repo.countTitleByPagesGreaterThan(400));
    }

    /**
     * Validate the expected number of distinct titles with pages greater than input value.
     */
    @Test
    void shouldReturnCountDistinctOfTitlesWithPagesGreaterThan() {
        Assertions.assertEquals(books.stream().filter(book -> book.getPages() > 400).distinct().count(),
            repo.countDistinctTitleByPagesGreaterThan(400));
    }

    /**
     * Validate the expected results are returned when searching for a list of titles
     * with pages greater than input value.
     */
    @Test
    void shouldReturnListOfDistinctTitlesWithPagesGreaterThan() {
        Assertions.assertTrue(repo.findDistinctTitleByPagesGreaterThan(400).containsAll(
            books.stream()
                .filter(book -> book.getPages() > 400)
                .map(Book::getTitle)
                .distinct().toList()));
    }

    /**
     * Validate the expected value is returned when getting max pages by author.
     */
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Test
    void shouldReturnMaxPagesByAuthor() {
        Assertions.assertEquals(books.stream()
                .filter(book -> book.getAuthor().equals(FRANK_HERBERT))
                .map(Book::getPages)
                .max(Comparator.comparing(Long::valueOf)).get().longValue(),
            repo.findMaxPagesByAuthor(FRANK_HERBERT));
    }

    /**
     * Validate the expected value is returned when getting min pages by author.
     */
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Test
    void shouldReturnMinPagesByAuthor() {
        Assertions.assertEquals(books.stream()
                .filter(book -> book.getAuthor().equals(FRANK_HERBERT))
                .map(Book::getPages)
                .min(Comparator.comparing(Long::valueOf)).get().longValue(),
            repo.findMinPagesByAuthor(FRANK_HERBERT));
    }

    /**
     * Validate the expected value is returned when getting sum pages by author.
     */
    @Test
    void shouldReturnSumPagesByAuthor() {
        Assertions.assertEquals(books.stream()
                .filter(book -> book.getAuthor().equals(FRANK_HERBERT))
                .map(Book::getPages)
                .reduce(0, Integer::sum).longValue(),
            repo.findSumPagesByAuthor(FRANK_HERBERT));
    }

    /**
     * Validate the expected value is returned when getting avg pages by author.
     */
    @Test
    void shouldReturnAvgPagesByAuthor() {
        Assertions.assertEquals(books.stream()
                .filter(book -> book.getAuthor().equals(FRANK_HERBERT))
                .collect(Collectors.averagingInt(Book::getPages)).longValue(),
            repo.findAvgPagesByAuthor(FRANK_HERBERT));
    }

    /**
     * Validate batch updates work as expected.
     */
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Test
    void shouldSupportBatchUpdates() {
        repo.updateByTitleStartingWith("Du", 700);
        Assertions.assertEquals(700, repo.findById(DUNE.getUuid()).get().getPages());
        Assertions.assertEquals(700, repo.findById(DUNE_MESSIAH.getUuid()).get().getPages());
    }

    /**
     * Validate single update with existing value returns the expected value and updates
     * the book.
     */
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Test
    void shouldSupportSingleUpdates() {
        Assertions.assertEquals(1, repo.update(DUNE.getUuid(), 999));
        Assertions.assertEquals(999, repo.findById(DUNE.getUuid()).get().getPages());
    }

    /**
     * Validate expected return value when the no entity matches.
     */
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Test
    void shouldSupportSingleUpdatesNoMatch() {
        Assertions.assertEquals(0, repo.update(new UUID(), 999));
        Assertions.assertEquals(DUNE.getPages(), repo.findById(DUNE.getUuid()).get().getPages());
    }

    /**
     * Validate batch deletes work as expected.
     */
    @Test
    void shouldSupportBatchDeletes() {
        repo.deleteByTitleStartingWith("Du");
        Assertions.assertEquals(2L, repo.count());
        Assertions.assertFalse(repo.findById(DUNE.getUuid()).isPresent());
        Assertions.assertFalse(repo.findById(DUNE_MESSIAH.getUuid()).isPresent());
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
        repo.saveBooks(setNewBooks);

        Assertions.assertTrue(repo.findByTitleIn(Arrays.asList("Children of Dune", "God Emperor of Dune")).containsAll(setNewBooks));
    }
}
