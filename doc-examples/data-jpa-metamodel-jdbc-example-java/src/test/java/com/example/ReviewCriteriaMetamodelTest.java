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
import com.example.repository.specification.ReviewSpecification;
import io.micronaut.entities.Book;
import io.micronaut.entities.Category;
import io.micronaut.entities.Review;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
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

    public ReviewCriteriaMetamodelTest(ReviewRepository reviewRepository,
                                       BookRepository bookRepository,
                                       CategoryRepository categoryRepository) {
        this.reviewRepository = reviewRepository;
        this.bookRepository = bookRepository;
        this.categoryRepository = categoryRepository;
    }

    @BeforeEach
    void cleanup() {
        reviewRepository.deleteAll();
        bookRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    @Test
    void canBuildCriteriaQueryUsingGeneratedStaticMetamodel_joinToBook_andFilter() {
        Category cat = new Category(100L, "Fiction", new ArrayList<>(), new byte[]{});
        Book book = new Book(200L, "Dune", 412, cat);

        Review r1 = new Review(1L, "alice", "Great", book);
        Review r2 = new Review(2L, "bob", "Okay", book);

        reviewRepository.saveAll(List.of(r1, r2));

        List<Review> result = reviewRepository.findAll(ReviewSpecification.withBookTitleEquals("Dune").and(ReviewSpecification.withReviewerEqual("alice")));

        assertEquals(1, result.size());
        assertNotNull(result.getFirst().getId());
        Assertions.assertEquals("alice", result.getFirst().getReviewer());
        Assertions.assertEquals("Great", result.getFirst().getContent());
        assertNotNull(result.getFirst().getBook());
        Assertions.assertEquals("Dune", result.getFirst().getBook().getTitle());
    }

}
