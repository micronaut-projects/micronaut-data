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
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.ListAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
        Category c1 = new Category();
        c1.setId(1L);
        c1.setName("Fiction");
        Category c2 = new Category();
        c2.setId(2L);

        categoryRepository.save(c1);
        categoryRepository.save(c2);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Category> cq = cb.createQuery(Category.class);
        Root<Category> root = cq.from(Category.class);

        cq.select(root)
            .where(cb.equal(root.get(Category_.name), "Fiction"));

        List<Category> result = entityManager.createQuery(cq).getResultList();
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals("Fiction", result.get(0).getName());
    }

    @Test
    void generatedMetamodelHasExpectedFields() throws Exception {
        assertNotNull(Category_.class.getDeclaredField("id"));
        assertNotNull(Category_.class.getDeclaredField("name"));
        assertNotNull(Category_.class.getDeclaredField("books"));

        assertEquals(SingularAttribute.class.getName(), Category_.class.getDeclaredField("id").getType().getName());
        assertEquals(SingularAttribute.class.getName(), Category_.class.getDeclaredField("name").getType().getName());
        assertEquals(ListAttribute.class.getName(), Category_.class.getDeclaredField("books").getType().getName());

        MetamodelAssertions.assertClassFieldIsEntityType(Category_.class, EntityType.class, Category.class);
    }

}
