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
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.MappedSuperclassType;
import jakarta.persistence.metamodel.SingularAttribute;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
public class ChildCriteriaMetamodelInheritanceTest {

    final ChildRepository childRepository;
    final EntityManager entityManager;

    public ChildCriteriaMetamodelInheritanceTest(ChildRepository childRepository,
                                                 EntityManager entityManager) {
        this.childRepository = childRepository;
        this.entityManager = entityManager;
    }

    @Test
    void canQueryByInheritedId_usingStaticMetamodel() {
        Child c1 = new Child();
        c1.setId(1L);
        c1.setName("Alice");
        c1.setAge(10L);

        Child c2 = new Child();
        c2.setId(2L);
        c2.setName("Bob");
        c2.setAge(20L);

        childRepository.save(c1);
        childRepository.save(c2);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Child> cq = cb.createQuery(Child.class);
        Root<Child> root = cq.from(Child.class);

        cq.select(root)
            .where(cb.equal(root.get(Child_.id), 2L));

        List<Child> result = entityManager.createQuery(cq).getResultList();

        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).getId());
        assertEquals("Bob", result.get(0).getName());
        assertEquals(20L, result.get(0).getAge());
    }

    @Test
    void canQueryByInheritedName_andDeclaredAge_usingStaticMetamodel() {
        Child c1 = new Child();
        c1.setId(3L);
        c1.setName("Carol");
        c1.setAge(30L);

        Child c2 = new Child();
        c2.setId(4L);
        c2.setName("Carol");
        c2.setAge(5L);

        childRepository.save(c1);
        childRepository.save(c2);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Child> cq = cb.createQuery(Child.class);
        Root<Child> root = cq.from(Child.class);

        cq.select(root)
            .where(cb.and(
                cb.equal(root.get(Child_.name), "Carol"),
                cb.greaterThan(root.get(Child_.age), 10L)
            ))
            .orderBy(cb.asc(root.get(Child_.id)));

        List<Child> result = entityManager.createQuery(cq).getResultList();

        assertEquals(1, result.size());
        assertEquals(3L, result.get(0).getId());
        assertEquals("Carol", result.get(0).getName());
        assertEquals(30L, result.get(0).getAge());
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
