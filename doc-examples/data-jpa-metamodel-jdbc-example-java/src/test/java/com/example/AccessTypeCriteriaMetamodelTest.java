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
import com.example.repository.specification.EmployeeFieldAccessSpecification;
import com.example.repository.specification.EmployeePropertyAccessSpecification;
import io.micronaut.entities.EmployeeFieldAccess;
import io.micronaut.entities.EmployeeMixedAccess;
import io.micronaut.entities.EmployeePropertyAccess;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.example.repository.specification.EmployeeMixedAccessSpecification.nameEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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

        List<EmployeeFieldAccess> result = employeeFieldAccessRepository.findAll(EmployeeFieldAccessSpecification.salaryBiggerThan(80_000d));

        assertEquals(1, result.size());
        assertEquals("Alice", result.getFirst().getName());
        assertNotNull(result.getFirst().getId());
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

        List<EmployeeMixedAccess> result = employeeMixedAccessRepository.findAll(nameEquals("Eve"));
        assertEquals(1, result.size());
        assertEquals("Eve", result.getFirst().getName());
        assertNotNull(result.getFirst().getId());
    }

}
