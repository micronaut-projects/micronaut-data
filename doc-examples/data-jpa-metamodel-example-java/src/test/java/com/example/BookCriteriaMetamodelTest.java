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
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest
public class BookCriteriaMetamodelTest {

    final BookRepository bookRepository;
    final CategoryRepository categoryRepository;
    final EntityManager entityManager;

    public BookCriteriaMetamodelTest(BookRepository bookRepository,
                                     CategoryRepository categoryRepository,
                                     EntityManager entityManager) {
        this.bookRepository = bookRepository;
        this.categoryRepository = categoryRepository;
        this.entityManager = entityManager;
    }

    @Test
    void canBuildCriteriaQueryUsingGeneratedStaticMetamodel_filterByTitle() {
        Category fiction = new Category();
        fiction.setId(1L);
        fiction.setName("Fiction");
        categoryRepository.save(fiction);

        Book b1 = new Book();
        b1.setId(10L);
        b1.setTitle("Dune");
        b1.setPages(412);
        b1.setCategory(fiction);

        Book b2 = new Book();
        b2.setId(11L);
        b2.setTitle("1984");
        b2.setPages(328);
        b2.setCategory(fiction);

        bookRepository.save(b1);
        bookRepository.save(b2);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Book> cq = cb.createQuery(Book.class);
        Root<Book> root = cq.from(Book.class);

        cq.select(root)
            .where(cb.equal(root.get(Book_.title), "Dune"));

        List<Book> result = entityManager.createQuery(cq).getResultList();
        assertEquals(1, result.size());
        assertEquals(10L, result.get(0).getId());
        assertEquals("Dune", result.get(0).getTitle());
        assertEquals(412, result.get(0).getPages());
    }

    @Test
    void canBuildCriteriaQueryUsingGeneratedStaticMetamodel_filterByPagesRange_andOrderBy() {
        Category fiction = new Category();
        fiction.setId(2L);
        fiction.setName("Fiction");
        categoryRepository.save(fiction);

        Book b1 = new Book();
        b1.setId(20L);
        b1.setTitle("Short Book");
        b1.setPages(120);
        b1.setCategory(fiction);

        Book b2 = new Book();
        b2.setId(21L);
        b2.setTitle("Medium Book");
        b2.setPages(250);
        b2.setCategory(fiction);

        Book b3 = new Book();
        b3.setId(22L);
        b3.setTitle("Long Book");
        b3.setPages(900);
        b3.setCategory(fiction);

        bookRepository.save(b1);
        bookRepository.save(b2);
        bookRepository.save(b3);

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
        assertEquals("Medium Book", result.get(0).getTitle());
        assertEquals(250, result.get(0).getPages());
    }

    @Test
    void canJoinManyToOneCategory_usingStaticMetamodel_andFilterOnCategoryName() {
        Category fiction = new Category();
        fiction.setId(3L);
        fiction.setName("Fiction");
        categoryRepository.save(fiction);

        Category nonFiction = new Category();
        nonFiction.setId(4L);
        nonFiction.setName("Non-Fiction");
        categoryRepository.save(nonFiction);

        Book b1 = new Book();
        b1.setId(30L);
        b1.setTitle("Novel");
        b1.setPages(300);
        b1.setCategory(fiction);

        Book b2 = new Book();
        b2.setId(31L);
        b2.setTitle("Biography");
        b2.setPages(280);
        b2.setCategory(nonFiction);

        bookRepository.save(b1);
        bookRepository.save(b2);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Book> cq = cb.createQuery(Book.class);

        Root<Book> bookRoot = cq.from(Book.class);
        Join<Book, Category> categoryJoin = bookRoot.join(Book_.category);

        cq.select(bookRoot)
            .where(cb.equal(categoryJoin.get(Category_.name), "Fiction"));

        List<Book> result = entityManager.createQuery(cq).getResultList();
        assertEquals(1, result.size());
        assertEquals("Novel", result.get(0).getTitle());
        assertEquals(300, result.get(0).getPages());
        assertNotNull(result.get(0).getCategory());
        assertEquals("Fiction", result.get(0).getCategory().getName());
    }

    @Test
    void canSelectScalarAttribute_usingStaticMetamodel() {
        Category fiction = new Category();
        fiction.setId(5L);
        fiction.setName("Fiction");
        categoryRepository.save(fiction);

        Book b1 = new Book();
        b1.setId(40L);
        b1.setTitle("Dune");
        b1.setPages(412);
        b1.setCategory(fiction);

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
        Category fiction = new Category();
        fiction.setId(6L);
        fiction.setName("Fiction");
        categoryRepository.save(fiction);

        for (long i = 1; i <= 5; i++) {
            Book b = new Book();
            b.setId(50L + i);
            b.setTitle("Book " + i);
            b.setPages((int) (100 + i));
            b.setCategory(fiction);
            bookRepository.save(b);
        }

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

    @Test
    void generatedMetamodelHasExpectedFields() throws Exception {
        assertNotNull(Book_.class.getDeclaredField("id"));
        assertNotNull(Book_.class.getDeclaredField("title"));
        assertNotNull(Book_.class.getDeclaredField("pages"));
        assertNotNull(Book_.class.getDeclaredField("category"));

        assertEquals(SingularAttribute.class.getName(), Book_.class.getDeclaredField("id").getType().getName());
        assertEquals(SingularAttribute.class.getName(), Book_.class.getDeclaredField("title").getType().getName());
        assertEquals(SingularAttribute.class.getName(), Book_.class.getDeclaredField("pages").getType().getName());
        assertEquals(SingularAttribute.class.getName(), Book_.class.getDeclaredField("category").getType().getName());

        MetamodelAssertions.assertClassFieldIsEntityType(Book_.class, EntityType.class, Book.class);
    }
}
