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
import io.micronaut.entities.Book;
import io.micronaut.entities.Category;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.example.repository.specification.CatgorySpecification.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@MicronautTest
public class CategoryCriteriaMetamodelTest {

    final CategoryRepository categoryRepository;
    private final BookRepository bookRepository;

    @BeforeEach
    void cleanup() {
        bookRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    public CategoryCriteriaMetamodelTest(CategoryRepository categoryRepository, BookRepository bookRepository) {
        this.categoryRepository = categoryRepository;
        this.bookRepository = bookRepository;
    }

    @Test
    void canBuildCriteriaQueryUsingGeneratedStaticMetamodel() {
        Category c1 = new Category(1L, "Fiction", new ArrayList<>(), new byte[]{});
        Category c2 = new Category(2L, "non-Fiction", new ArrayList<>(), new byte[]{});

        categoryRepository.saveAll(List.of(c1, c2));

        List<Category> result = categoryRepository.findAll(nameEquals("Fiction"));

        assertEquals(1, result.size());
        Assertions.assertEquals(1L, result.getFirst().getId());
        Assertions.assertEquals("Fiction", result.getFirst().getName());
    }

    @Test
    void canJoinBooksList_usingStaticMetamodel_andFilterByBookTitle() {
        Category fiction = new Category(1L, "Fiction", new ArrayList<>(), new byte[]{});
        Category history = new Category(2L, "History", new ArrayList<>(), new byte[]{});

        Book b1 = new Book(1L, "Dune", 412, fiction);
        Book b2 = new Book(2L, "1984", 328, fiction);
        Book b3 = new Book(3L, "Sapiens", 450, history);

        bookRepository.saveAll(List.of(b1, b2, b3));

        List<Category> result = categoryRepository.findAll(withBooksTitleEquals("Dune"));

        assertEquals(1, result.size());
        Assertions.assertEquals("Fiction", result.getFirst().getName());
    }

    @Test
    void canQueryDistinctCategories_withLeftJoinBooks_usingStaticMetamodel() {
        Category empty = new Category(20L, "Empty", new ArrayList<>(), new byte[]{});
        Category fiction = new Category(21L, "Fiction", new ArrayList<>(), new byte[]{});

        Book b1 = new Book(200L, "Dune", 412, fiction);
        Book b2 = new Book(201L, "Dune", 412, fiction);

        bookRepository.saveAll(List.of(b1, b2));
        categoryRepository.save(empty);

        List<Long> ids = categoryRepository.findAll(withBooks())
            .stream().map(Category::getId).toList();
        assertEquals(List.of(21L), ids);
    }

    @Test
    void joiningBooksDoesNotDuplicateCategoryRows_whenUsingDistinct() {
        Category fiction = new Category(30L, "Fiction", new ArrayList<>(), new byte[]{});

        Book b = new Book(300L, "Dune", 412, fiction);
        Book b1 = new Book(301L, "1984", 328, fiction);
        Book b2 = new Book(302L, "Brave New World", 288, fiction);

        bookRepository.saveAll(List.of(b, b1, b2));

        List<Category> result = categoryRepository.findAll(withBooks().and(idEquals(30L)));
        assertEquals(1, result.size(), "Without distinct(), a join on a collection usually duplicates root rows");
        Assertions.assertEquals(30L, result.getFirst().getId());
        Assertions.assertEquals(3, result.getFirst().getBooks().size());
    }

    @Test
    void bytesFieldIsGeneratedAsSingularAttribute_andQueryable() {
        Category c1 = new Category(40L, "HasBytes", new ArrayList<>(), new byte[]{1, 2, 3});
        Category c2 = new Category(41L, "NoBytes", new ArrayList<>(), new byte[]{});

        categoryRepository.saveAll(List.of(c1, c2));

        List<Category> result = categoryRepository.findAll(bytesNotNull());
        assertFalse(result.isEmpty());
    }

}
