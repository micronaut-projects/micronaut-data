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
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.example.repository.specification.ReviewSpecification.withBookTitleEquals;
import static com.example.repository.specification.ReviewSpecification.withReviewerEqual;
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
        Category cat = new Category(100L, "Fiction", new ArrayList<>(), null);
        Book book = new Book(200L, "Dune", 412, cat);

        Review r1 = new Review(1L, "alice", "Great", book);
        Review r2 = new Review(2L, "bob", "Okay", book);

        reviewRepository.saveAll(List.of(r1, r2));

        List<Review> result = reviewRepository.findAll(withBookTitleEquals("Dune").and(withReviewerEqual("alice")));

        assertEquals(1, result.size());
        assertNotNull(result.getFirst().id());
        assertEquals("alice", result.getFirst().reviewer());
        assertEquals("Great", result.getFirst().content());
        assertNotNull(result.getFirst().book());
        assertEquals("Dune", result.getFirst().book().getTitle());
    }

    @Test
    void generatedMetamodelHasExpectedFields() throws Exception {
        assertNotNull(Review_.class.getDeclaredField("id"));
        assertNotNull(Review_.class.getDeclaredField("reviewer"));
        assertNotNull(Review_.class.getDeclaredField("content"));
        assertNotNull(Review_.class.getDeclaredField("book"));

        assertEquals(SingularAttribute.class.getName(), Review_.class.getDeclaredField("id").getType().getName());
        assertEquals(SingularAttribute.class.getName(), Review_.class.getDeclaredField("reviewer").getType().getName());
        assertEquals(SingularAttribute.class.getName(), Review_.class.getDeclaredField("content").getType().getName());
        assertEquals(SingularAttribute.class.getName(), Review_.class.getDeclaredField("book").getType().getName());

        MetamodelAssertions.assertClassFieldIsEntityType(Review_.class, EntityType.class, Review.class);

    }
}
