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
import com.example.repository.ReviewRepository;
import io.micronaut.entities.*;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest
public class ReviewCriteriaMetamodelTest {

    final ReviewRepository reviewRepository;
    final BookRepository bookRepository;
    final CategoryRepository categoryRepository;
    final EntityManager entityManager;

    public ReviewCriteriaMetamodelTest(ReviewRepository reviewRepository,
                                       BookRepository bookRepository,
                                       CategoryRepository categoryRepository,
                                       EntityManager entityManager) {
        this.reviewRepository = reviewRepository;
        this.bookRepository = bookRepository;
        this.categoryRepository = categoryRepository;
        this.entityManager = entityManager;
    }

    @Test
    void canBuildCriteriaQueryUsingGeneratedStaticMetamodel_joinToBook_andFilter() {
        Category cat = new Category(100L, "Fiction", new ArrayList<>(), new byte[]{});
        Book book = new Book(200L, "Dune", 412, cat);

        bookRepository.save(book);

        Review r1 = new Review(1L, "alice", "Great", book);
        Review r2 = new Review(2L, "bob", "Okay", book);

        reviewRepository.saveAll(List.of(r1, r2));

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Review> cq = cb.createQuery(Review.class);
        Root<Review> root = cq.from(Review.class);

        Join<Review, Book> bookJoin = root.join(Review_.book, JoinType.INNER);

        cq.select(root)
            .where(cb.and(
                cb.equal(bookJoin.get(Book_.title), "Dune"),
                cb.equal(root.get(Review_.reviewer), "alice")
            ));

        List<Review> result = entityManager.createQuery(cq).getResultList();
        assertEquals(1, result.size());
        assertNotNull(result.getFirst().getId());
        assertEquals("alice", result.getFirst().getReviewer());
        assertEquals("Great", result.getFirst().getContent());
        assertNotNull(result.getFirst().getBook());
        assertEquals("Dune", result.getFirst().getBook().getTitle());
    }

}
