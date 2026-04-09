/*
 * Copyright 2017-2026 original authors
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.example;

import com.example.repository.BookRepository;
import com.example.repository.CategoryRepository;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.entities.Book;
import io.micronaut.entities.Book_;
import io.micronaut.entities.Category;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.example.repository.specification.BookSpecification.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest
public class BookCriteriaMetamodelTest {

    final BookRepository bookRepository;
    final CategoryRepository categoryRepository;

    public BookCriteriaMetamodelTest(BookRepository bookRepository,
                                     CategoryRepository categoryRepository) {
        this.bookRepository = bookRepository;
        this.categoryRepository = categoryRepository;
    }

    @BeforeEach
    void cleanup() {
        bookRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    @Test
    void canBuildCriteriaQueryUsingGeneratedStaticMetamodel_filterByTitle() {
        Category fiction = new Category(1L, "Fiction", new ArrayList<>(), new byte[]{});

        Book b1 = new Book(10L, "Dune", 412, fiction);
        Book b2 = new Book(11L, "1984", 328, fiction);

        bookRepository.saveAll(List.of(b1, b2));

        List<Book> books = bookRepository.findAll(titleEquals("Dune"));

        assertEquals(1, books.size());
        Assertions.assertEquals(10L, books.getFirst().getId());
        Assertions.assertEquals("Dune", books.getFirst().getTitle());
        Assertions.assertEquals(412, books.getFirst().getPages());
    }

    @Test
    void canBuildCriteriaQueryUsingGeneratedStaticMetamodel_filterByPagesRange_andOrderBy() {
        Category fiction = new Category(2L, "Fiction", null, new byte[]{});

        Book b1 = new Book(20L, "Short Book", 120, fiction);
        Book b2 = new Book(21L, "Medium Book", 250, fiction);
        Book b3 = new Book(22L, "Long Book", 900, fiction);
        Book b4 = new Book(23L, "Medium Book", 300, fiction);

        bookRepository.saveAll(List.of(b1, b2, b3, b4));

        List<Book> result = bookRepository.findAll(pagesGreaterThanOrEqualTo(200).and(pagesLessThan(800)),
            Sort.of(Sort.Order.asc(Book_.PAGES)));

        assertEquals(2, result.size());
        Assertions.assertEquals("Medium Book", result.getFirst().getTitle());
        Assertions.assertEquals(250, result.getFirst().getPages());
    }

    @Test
    void canJoinManyToOneCategory_usingStaticMetamodel_andFilterOnCategoryName() {
        Category fiction = new Category(3L, "Fiction", null, new byte[]{});
        Category nonFiction = new Category(4l, "Non-Fiction", null, new byte[]{});

        Book b1 = new Book(30L, "Novel", 300, fiction);
        Book b2 = new Book(31L, "Biography", 280, nonFiction);

        bookRepository.saveAll(List.of(b1, b2));

        List<Book> result = bookRepository.findAll(withCategoryName("Fiction"));

        assertEquals(1, result.size());
        Assertions.assertEquals("Novel", result.getFirst().getTitle());
        Assertions.assertEquals(300, result.getFirst().getPages());
        assertNotNull(result.getFirst().getCategory());
        Assertions.assertEquals("Fiction", result.getFirst().getCategory().getName());
    }

    @Test
    void canUsePaginationWithCriteriaQueryBuiltFromStaticMetamodel() {
        Category fiction = new Category(6L, "Fiction", null, new byte[]{});
        List<Book> books = new ArrayList<>();
        for (int i = 0; i <= 5; i++) {
            Book b = new Book(50L + i, "Book" + i, 100 + i, fiction);
            books.add(b);
        }
        bookRepository.saveAll(books);

        List<Book> page = bookRepository.findAll((root, criteriaBuilder) ->
                criteriaBuilder.greaterThan(root.get(Book_.id), 0L),
            Pageable.from(1, 2, Sort.of(Sort.Order.asc(Book_.ID)))).getContent();

        assertEquals(2, page.size());
        Assertions.assertEquals(52L, page.get(0).getId());
        Assertions.assertEquals(53L, page.get(1).getId());
    }

}
