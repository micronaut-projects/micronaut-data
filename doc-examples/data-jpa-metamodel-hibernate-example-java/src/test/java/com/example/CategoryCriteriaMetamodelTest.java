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

import com.example.repository.CategoryRepository;
import io.micronaut.entities.Category;
import io.micronaut.entities.Category_;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
public class CategoryCriteriaMetamodelTest {

    final CategoryRepository categoryRepository;
    final EntityManager entityManager;

    public CategoryCriteriaMetamodelTest(CategoryRepository categoryRepository, EntityManager entityManager) {
        this.categoryRepository = categoryRepository;
        this.entityManager = entityManager;
    }

    @Test
    void canBuildCriteriaQueryUsingGeneratedStaticMetamodel() {
        Category c1 = new Category(1L, "Fiction", new ArrayList<>(), new byte[]{});
        Category c2 = new Category(2L, null, new ArrayList<>(), new byte[]{});

        categoryRepository.saveAll(List.of(c1, c2));

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Category> cq = cb.createQuery(Category.class);
        Root<Category> root = cq.from(Category.class);

        cq.select(root)
            .where(cb.equal(root.get(Category_.name), "Fiction"));

        List<Category> result = entityManager.createQuery(cq).getResultList();
        assertEquals(1, result.size());
        assertEquals(1L, result.getFirst().getId());
        assertEquals("Fiction", result.getFirst().getName());
    }

}
