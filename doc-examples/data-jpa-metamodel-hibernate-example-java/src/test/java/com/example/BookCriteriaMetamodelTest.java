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
import io.micronaut.entities.Book;
import io.micronaut.entities.Book_;
import io.micronaut.entities.Category;
import io.micronaut.entities.Category_;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest
public class BookCriteriaMetamodelTest {

    final BookRepository bookRepository;
    final EntityManager entityManager;

    public BookCriteriaMetamodelTest(BookRepository bookRepository,
                                     EntityManager entityManager) {
        this.bookRepository = bookRepository;
        this.entityManager = entityManager;
    }

    @Test
    void canBuildCriteriaQueryUsingGeneratedStaticMetamodel_filterByTitle() {
        Category fiction = new Category(1L, "Fiction", new ArrayList<>(), new byte[]{});

        Book b1 = new Book(10L, "Dune", 412, fiction);
        Book b2 = new Book(11L, "1984", 328, fiction);

        bookRepository.saveAll(List.of(b1, b2));

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Book> cq = cb.createQuery(Book.class);
        Root<Book> root = cq.from(Book.class);

        cq.select(root)
            .where(cb.equal(root.get(Book_.title), "Dune"));

        List<Book> result = entityManager.createQuery(cq).getResultList();
        assertEquals(1, result.size());
        assertEquals(10L, result.getFirst().getId());
        assertEquals("Dune", result.getFirst().getTitle());
        assertEquals(412, result.getFirst().getPages());
    }

    @Test
    void canBuildCriteriaQueryUsingGeneratedStaticMetamodel_filterByPagesRange_andOrderBy() {
        Category fiction = new Category(2L, "Fiction", new ArrayList<>(), new byte[]{});

        Book b1 = new Book(20L, "Short Book", 120, fiction);
        Book b2 = new Book(21L, "Medium Book", 250, fiction);
        Book b3 = new Book(22L, "Long Book", 900, fiction);

        bookRepository.saveAll(List.of(b1, b2, b3));

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Book> cq = cb.createQuery(Book.class);
        Root<Book> root = cq.from(Book.class);

        cq.select(root)
            .where(cb.and(
                cb.greaterThanOrEqualTo(root.get(Book_.pages), 200),
                cb.lessThan(root.get(Book_.pages), 800)
            ))
            .orderBy(cb.asc(root.get(Book_.pages)));

        List<Book> result = entityManager.createQuery(cq).getResultList();
        assertEquals(1, result.size());
        assertEquals("Medium Book", result.getFirst().getTitle());
        assertEquals(250, result.getFirst().getPages());
    }

    @Test
    void canJoinManyToOneCategory_usingStaticMetamodel_andFilterOnCategoryName() {
        Category fiction = new Category(3L, "Fiction", new ArrayList<>(), new byte[]{});
        Category nonFiction = new Category(4L, "Non-Fiction", new ArrayList<>(), new byte[]{});

        Book b1 = new Book(30L, "Novel", 300, fiction);
        Book b2 = new Book(31L, "Biography", 280, nonFiction);

        bookRepository.saveAll(List.of(b1, b2));

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Book> cq = cb.createQuery(Book.class);

        Root<Book> bookRoot = cq.from(Book.class);
        Join<Book, Category> categoryJoin = bookRoot.join(Book_.category);

        cq.select(bookRoot)
            .where(cb.equal(categoryJoin.get(Category_.name), "Fiction"));

        List<Book> result = entityManager.createQuery(cq).getResultList();
        assertEquals(1, result.size());
        assertEquals("Novel", result.getFirst().getTitle());
        assertEquals(300, result.getFirst().getPages());
        assertNotNull(result.getFirst().getCategory());
        assertEquals("Fiction", result.getFirst().getCategory().getName());
    }

    @Test
    void canSelectScalarAttribute_usingStaticMetamodel() {
        Category fiction = new Category(5L, "Fiction", new ArrayList<>(), new byte[]{});

        Book b1 = new Book(40L, "Dune", 412, fiction);

        bookRepository.save(b1);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<String> cq = cb.createQuery(String.class);
        Root<Book> root = cq.from(Book.class);

        cq.select(root.get(Book_.title))
            .where(cb.equal(root.get(Book_.id), 40L));

        List<String> titles = entityManager.createQuery(cq).getResultList();
        assertEquals(List.of("Dune"), titles);
    }

    @Test
    void canUsePaginationWithCriteriaQueryBuiltFromStaticMetamodel() {
        Category fiction = new Category(6L, "Fiction", new ArrayList<>(), new byte[]{});

        List<Book> books = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Book b = new Book(50L + i, "Book" + i, 100 + i, fiction);
            books.add(b);
        }
        bookRepository.saveAll(books);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Book> cq = cb.createQuery(Book.class);
        Root<Book> root = cq.from(Book.class);

        cq.select(root)
            .orderBy(cb.asc(root.get(Book_.id)));

        TypedQuery<Book> query = entityManager.createQuery(cq);
        query.setFirstResult(1);
        query.setMaxResults(2);

        List<Book> page = query.getResultList();
        assertEquals(2, page.size());
        assertEquals(52L, page.get(0).getId());
        assertEquals(53L, page.get(1).getId());
    }

}
