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
import com.example.repository.specification.EmployeeMixedAccessSpecification;
import com.example.repository.specification.EmployeePropertyAccessSpecification;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.example.repository.specification.EmployeeFieldAccessSpecification.salaryBiggerThan;
import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
public class AccessTypeCriteriaMetamodelTest {

    final EmployeeFieldAccessRepository employeeFieldAccessRepository;
    final EmployeePropertyAccessRepository employeePropertyAccessRepository;
    final EmployeeMixedAccessRepository employeeMixedAccessRepository;
    final EmployeeMixedAccessEmbeddedIdRepository employeeMixedAccessEmbeddedIdRepository;

    public AccessTypeCriteriaMetamodelTest(EmployeeFieldAccessRepository employeeFieldAccessRepository,
                                           EmployeePropertyAccessRepository employeePropertyAccessRepository,
                                           EmployeeMixedAccessRepository employeeMixedAccessRepository,
                                           EmployeeMixedAccessEmbeddedIdRepository employeeMixedAccessEmbeddedIdRepository) {
        this.employeeFieldAccessRepository = employeeFieldAccessRepository;
        this.employeePropertyAccessRepository = employeePropertyAccessRepository;
        this.employeeMixedAccessRepository = employeeMixedAccessRepository;
        this.employeeMixedAccessEmbeddedIdRepository = employeeMixedAccessEmbeddedIdRepository;
    }

    @BeforeEach
    void cleanup() {
        employeeMixedAccessRepository.deleteAll();
        employeeMixedAccessEmbeddedIdRepository.deleteAll();
        employeeFieldAccessRepository.deleteAll();
        employeePropertyAccessRepository.deleteAll();
    }

    @Test
    void fieldAccessEntity_canQueryUsingStaticMetamodel() {
        EmployeeFieldAccess e1 = new EmployeeFieldAccess(null, "Alice", 100_000d);
        EmployeeFieldAccess e2 = new EmployeeFieldAccess(null, "Bob", 50_000d);

        employeeFieldAccessRepository.saveAll(List.of(e1, e2));

        List<EmployeeFieldAccess> result = employeeFieldAccessRepository.findAll(salaryBiggerThan(80_000d));

        assertEquals(1, result.size());
        assertEquals("Alice", result.getFirst().getName());
        assertTrue(result.getFirst().getId() != null);
    }

    @Test
    void propertyAccessEntity_canQueryUsingStaticMetamodel() {
        EmployeePropertyAccess e1 = new EmployeePropertyAccess(null, "Carol", 120_000d);
        EmployeePropertyAccess e2 = new EmployeePropertyAccess(null, "Dave", 70_000d);

        employeePropertyAccessRepository.saveAll(List.of(e1, e2));

        List<EmployeePropertyAccess> result = employeePropertyAccessRepository.findAll(EmployeePropertyAccessSpecification.nameEquals("Carol"));

        assertEquals(1, result.size());
        assertEquals("Carol", result.getFirst().getName());
        assertEquals(120_000d, result.getFirst().getSalary());
        assertNotNull(result.getFirst().getId());
    }

    @Test
    void mixedAccessEntity_defaultPropertyAccess_fieldWithoutAccessorsIsNotPersistent() {
        EmployeeMixedAccess e = new EmployeeMixedAccess(null, "Eve", 90_000d);

        employeeMixedAccessRepository.save(e);

        List<EmployeeMixedAccess> result = employeeMixedAccessRepository.findAll(EmployeeMixedAccessSpecification.nameEquals("Eve"));
        assertEquals(1, result.size());
        assertEquals("Eve", result.getFirst().getName());
        assertNotNull(result.getFirst().getId());
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
