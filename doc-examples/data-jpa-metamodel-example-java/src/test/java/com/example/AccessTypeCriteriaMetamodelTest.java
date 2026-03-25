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

import com.example.repository.EmployeeFieldAccessRepository;
import com.example.repository.EmployeeMixedAccessEmbeddedIdRepository;
import com.example.repository.EmployeeMixedAccessRepository;
import com.example.repository.EmployeePropertyAccessRepository;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
public class AccessTypeCriteriaMetamodelTest {

    final EmployeeFieldAccessRepository employeeFieldAccessRepository;
    final EmployeePropertyAccessRepository employeePropertyAccessRepository;
    final EmployeeMixedAccessRepository employeeMixedAccessRepository;
    final EmployeeMixedAccessEmbeddedIdRepository employeeMixedAccessEmbeddedIdRepository;
    final EntityManager entityManager;

    public AccessTypeCriteriaMetamodelTest(EmployeeFieldAccessRepository employeeFieldAccessRepository,
                                           EmployeePropertyAccessRepository employeePropertyAccessRepository,
                                           EmployeeMixedAccessRepository employeeMixedAccessRepository,
                                           EmployeeMixedAccessEmbeddedIdRepository employeeMixedAccessEmbeddedIdRepository,
                                           EntityManager entityManager) {
        this.employeeFieldAccessRepository = employeeFieldAccessRepository;
        this.employeePropertyAccessRepository = employeePropertyAccessRepository;
        this.employeeMixedAccessRepository = employeeMixedAccessRepository;
        this.employeeMixedAccessEmbeddedIdRepository = employeeMixedAccessEmbeddedIdRepository;
        this.entityManager = entityManager;
    }

    @Test
    void fieldAccessEntity_canQueryUsingStaticMetamodel() {
        EmployeeFieldAccess e1 = new EmployeeFieldAccess();
        e1.setName("Alice");
        e1.setSalary(100_000d);

        EmployeeFieldAccess e2 = new EmployeeFieldAccess();
        e2.setName("Bob");
        e2.setSalary(50_000d);

        employeeFieldAccessRepository.save(e1);
        employeeFieldAccessRepository.save(e2);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<EmployeeFieldAccess> cq = cb.createQuery(EmployeeFieldAccess.class);
        Root<EmployeeFieldAccess> root = cq.from(EmployeeFieldAccess.class);

        cq.select(root)
            .where(cb.greaterThan(root.get(EmployeeFieldAccess_.salary), 80_000d))
            .orderBy(cb.asc(root.get(EmployeeFieldAccess_.name)));

        List<EmployeeFieldAccess> result = entityManager.createQuery(cq).getResultList();

        assertEquals(1, result.size());
        assertEquals("Alice", result.get(0).getName());
        assertTrue(result.get(0).getId() != null);
    }

    @Test
    void propertyAccessEntity_canQueryUsingStaticMetamodel() {
        EmployeePropertyAccess e1 = new EmployeePropertyAccess();
        e1.setName("Carol");
        e1.setSalary(120_000d);

        EmployeePropertyAccess e2 = new EmployeePropertyAccess();
        e2.setName("Dave");
        e2.setSalary(70_000d);

        employeePropertyAccessRepository.save(e1);
        employeePropertyAccessRepository.save(e2);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<EmployeePropertyAccess> cq = cb.createQuery(EmployeePropertyAccess.class);
        Root<EmployeePropertyAccess> root = cq.from(EmployeePropertyAccess.class);

        cq.select(root)
            .where(cb.equal(root.get(EmployeePropertyAccess_.name), "Carol"));

        List<EmployeePropertyAccess> result = entityManager.createQuery(cq).getResultList();

        assertEquals(1, result.size());
        assertEquals("Carol", result.get(0).getName());
        assertEquals(120_000d, result.get(0).getSalary(), 0.001);
        assertNotNull(result.get(0).getId());
    }

    @Test
    void mixedAccessEntity_defaultPropertyAccess_fieldWithoutAccessorsIsNotPersistent() {
        EmployeeMixedAccess e = new EmployeeMixedAccess();
        e.setName("Eve");
        e.setSalary(90_000d);

        employeeMixedAccessRepository.save(e);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<EmployeeMixedAccess> cq = cb.createQuery(EmployeeMixedAccess.class);
        Root<EmployeeMixedAccess> root = cq.from(EmployeeMixedAccess.class);

        cq.select(root)
            .where(cb.equal(root.get(EmployeeMixedAccess_.name), "Eve"));

        List<EmployeeMixedAccess> result = entityManager.createQuery(cq).getResultList();
        assertEquals(1, result.size());
        assertEquals("Eve", result.get(0).getName());
        assertNotNull(result.get(0).getId());
    }

    @Test
    void generatedMetamodelHasExpectedFields_fieldAccess() throws Exception {
        assertNotNull(EmployeeFieldAccess_.class.getDeclaredField("id"));
        assertNotNull(EmployeeFieldAccess_.class.getDeclaredField("name"));
        assertNotNull(EmployeeFieldAccess_.class.getDeclaredField("salary"));
        assertNotNull(EmployeeFieldAccess_.class.getDeclaredField("class_"));


        assertEquals(SingularAttribute.class.getName(),
            EmployeeFieldAccess_.class.getDeclaredField("id").getType().getName());
        assertEquals(SingularAttribute.class.getName(),
            EmployeeFieldAccess_.class.getDeclaredField("name").getType().getName());
        assertEquals(SingularAttribute.class.getName(),
            EmployeeFieldAccess_.class.getDeclaredField("salary").getType().getName());
        MetamodelAssertions.assertClassFieldIsEntityType(EmployeeFieldAccess_.class, EntityType.class, EmployeeFieldAccess.class);
    }

    @Test
    void generatedMetamodelHasExpectedFields_propertyAccess() throws Exception {
        assertNotNull(EmployeePropertyAccess_.class.getDeclaredField("id"));
        assertNotNull(EmployeePropertyAccess_.class.getDeclaredField("name"));
        assertNotNull(EmployeePropertyAccess_.class.getDeclaredField("salary"));

        assertEquals(SingularAttribute.class.getName(),
            EmployeePropertyAccess_.class.getDeclaredField("id").getType().getName());
        assertEquals(SingularAttribute.class.getName(),
            EmployeePropertyAccess_.class.getDeclaredField("name").getType().getName());
        assertEquals(SingularAttribute.class.getName(),
            EmployeePropertyAccess_.class.getDeclaredField("salary").getType().getName());

        MetamodelAssertions.assertClassFieldIsEntityType(EmployeePropertyAccess_.class, EntityType.class, EmployeePropertyAccess.class);

    }

    @Test
    void generatedMetamodelHasExpectedFields_mixedAccess_andDoesNotContainUnmappedField() throws Exception {
        assertNotNull(EmployeeMixedAccess_.class.getDeclaredField("id"));
        assertNotNull(EmployeeMixedAccess_.class.getDeclaredField("name"));
        assertNotNull(EmployeeMixedAccess_.class.getDeclaredField("salary"));
        assertNotNull(EmployeeMixedAccess_.class.getDeclaredField("fieldAnnotated"));

        assertEquals(SingularAttribute.class.getName(),
            EmployeeMixedAccess_.class.getDeclaredField("id").getType().getName());
        assertEquals(SingularAttribute.class.getName(),
            EmployeeMixedAccess_.class.getDeclaredField("name").getType().getName());
        assertEquals(SingularAttribute.class.getName(),
            EmployeeMixedAccess_.class.getDeclaredField("salary").getType().getName());
        assertEquals(SingularAttribute.class.getName(),
            EmployeeMixedAccess_.class.getDeclaredField("fieldAnnotated").getType().getName());

        assertThrows(NoSuchFieldException.class,
            () -> EmployeeMixedAccess_.class.getDeclaredField("fieldWithoutAccessors"));

        MetamodelAssertions.assertClassFieldIsEntityType(EmployeeMixedAccess_.class, EntityType.class, EmployeeMixedAccess.class);

    }

    @Test
    void generatedMetamodelHasExpectedFields_mixedAccessEmbeddableId_andDoesNotContainUnmappedField() throws Exception {
        assertNotNull(EmployeeMixedAccess_.class.getDeclaredField("id"));
        assertNotNull(EmployeeMixedAccess_.class.getDeclaredField("name"));
        assertNotNull(EmployeeMixedAccess_.class.getDeclaredField("salary"));
        assertNotNull(EmployeeMixedAccess_.class.getDeclaredField("fieldAnnotated"));

        assertEquals(SingularAttribute.class.getName(),
            EmployeeMixedAccess_.class.getDeclaredField("id").getType().getName());
        assertEquals(SingularAttribute.class.getName(),
            EmployeeMixedAccess_.class.getDeclaredField("name").getType().getName());
        assertEquals(SingularAttribute.class.getName(),
            EmployeeMixedAccess_.class.getDeclaredField("salary").getType().getName());
        assertEquals(SingularAttribute.class.getName(),
            EmployeeMixedAccess_.class.getDeclaredField("fieldAnnotated").getType().getName());

        assertThrows(NoSuchFieldException.class,
            () -> EmployeeMixedAccess_.class.getDeclaredField("fieldWithoutAccessors"));

        MetamodelAssertions.assertClassFieldIsEntityType(EmployeeMixedAccessEmbeddedId_.class, EntityType.class, EmployeeMixedAccessEmbeddedId.class);
    }
}
