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

import com.example.repository.ChildRepository;
import io.micronaut.data.model.Sort;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.MappedSuperclassType;
import jakarta.persistence.metamodel.SingularAttribute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.example.repository.specification.ChildSpecification.*;
import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
public class ChildCriteriaMetamodelInheritanceTest {

    final ChildRepository childRepository;

    public ChildCriteriaMetamodelInheritanceTest(ChildRepository childRepository) {
        this.childRepository = childRepository;
    }

    @BeforeEach
    void cleanup() {
        childRepository.deleteAll();
    }

    @Test
    void canQueryByInheritedId_usingStaticMetamodel() {
        Child c1 = new Child(1L, "Alice", 10L);
        Child c2 = new Child(2L, "Bob", 20L);

        childRepository.saveAll(List.of(c1, c2));

        List<Child> result = childRepository.findAll(idEquals(2L));

        assertEquals(1, result.size());
        assertEquals(2L, result.getFirst().getId());
        assertEquals("Bob", result.getFirst().getName());
        assertEquals(20L, result.getFirst().getAge());
    }

    @Test
    void canQueryByInheritedName_andDeclaredAge_usingStaticMetamodel() {
        Child c1 = new Child(3L, "Carol", 30L);
        Child c2 = new Child(4L, "Carol", 5L);

        childRepository.saveAll(List.of(c1, c2));

        List<Child> result = childRepository.findAll(nameEquals("Carol").and(ageGreaterThan(10L)), Sort.of(Sort.Order.asc(Child_.ID)));

        assertEquals(1, result.size());
        assertEquals(3L, result.getFirst().getId());
        assertEquals("Carol", result.getFirst().getName());
        assertEquals(30L, result.getFirst().getAge());
    }

    @Test
    void generatedMetamodelHasExpectedFields_includingInheritedFromMappedSuperclass() throws Exception {

        assertNotNull(Child_.class.getField("id"));
        assertNotNull(Child_.class.getField("name"));
        assertNotNull(Child_.class.getDeclaredField("age"));

        assertEquals(SingularAttribute.class.getName(), Child_.class.getField("id").getType().getName());
        assertEquals(SingularAttribute.class.getName(), Child_.class.getField("name").getType().getName());
        assertEquals(SingularAttribute.class.getName(), Child_.class.getDeclaredField("age").getType().getName());

        MetamodelAssertions.assertClassFieldIsEntityType(Child_.class, EntityType.class, Child.class);
    }

    @Test
    void mappedSuperclassMetamodel_optional() throws Exception {
        Class<?> parentMetamodel = Class.forName("com.example.Parent_");

        assertNotNull(parentMetamodel.getDeclaredField("id"));
        assertNotNull(parentMetamodel.getDeclaredField("name"));

        assertEquals(SingularAttribute.class.getName(),
            parentMetamodel.getDeclaredField("id").getType().getName());
        assertEquals(SingularAttribute.class.getName(),
            parentMetamodel.getDeclaredField("name").getType().getName());

        assertThrows(NoSuchFieldException.class, () -> Parent_.class.getDeclaredField("age"));
        MetamodelAssertions.assertClassFieldIsEntityType(Parent_.class, MappedSuperclassType.class, Parent.class);


    }
}
