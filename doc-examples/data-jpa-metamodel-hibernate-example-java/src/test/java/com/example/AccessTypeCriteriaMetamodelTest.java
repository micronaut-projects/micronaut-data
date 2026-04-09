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
import io.micronaut.entities.*;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
        EmployeeFieldAccess e1 = new EmployeeFieldAccess(null, "Alice", 100_000d);

        EmployeeFieldAccess e2 = new EmployeeFieldAccess(null, "Bob", 50_000d);

        employeeFieldAccessRepository.saveAll(List.of(e1, e2));

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<EmployeeFieldAccess> cq = cb.createQuery(EmployeeFieldAccess.class);
        Root<EmployeeFieldAccess> root = cq.from(EmployeeFieldAccess.class);

        cq.select(root)
            .where(cb.greaterThan(root.get(EmployeeFieldAccess_.salary), 80_000d))
            .orderBy(cb.asc(root.get(EmployeeFieldAccess_.name)));

        List<EmployeeFieldAccess> result = entityManager.createQuery(cq).getResultList();

        assertEquals(1, result.size());
        assertEquals("Alice", result.getFirst().getName());
        assertNotNull(result.getFirst().getId());
    }

    @Test
    void propertyAccessEntity_canQueryUsingStaticMetamodel() {
        EmployeePropertyAccess e1 = new EmployeePropertyAccess(null, "Carol", 120_000d);
        EmployeePropertyAccess e2 = new EmployeePropertyAccess(null, "Dave", 70_000d);

        employeePropertyAccessRepository.saveAll(List.of(e1, e2));

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<EmployeePropertyAccess> cq = cb.createQuery(EmployeePropertyAccess.class);
        Root<EmployeePropertyAccess> root = cq.from(EmployeePropertyAccess.class);

        cq.select(root)
            .where(cb.equal(root.get(EmployeePropertyAccess_.name), "Carol"));

        List<EmployeePropertyAccess> result = entityManager.createQuery(cq).getResultList();

        assertEquals(1, result.size());
        assertEquals("Carol", result.getFirst().getName());
        assertEquals(120_000d, result.getFirst().getSalary(), 0.001);
        assertNotNull(result.getFirst().getId());
    }

    @Test
    void mixedAccessEntity_defaultPropertyAccess_fieldWithoutAccessorsIsNotPersistent() {
        EmployeeMixedAccess e = new EmployeeMixedAccess(null, "Eve", 90_000d);

        employeeMixedAccessRepository.save(e);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<EmployeeMixedAccess> cq = cb.createQuery(EmployeeMixedAccess.class);
        Root<EmployeeMixedAccess> root = cq.from(EmployeeMixedAccess.class);

        cq.select(root)
            .where(cb.equal(root.get(EmployeeMixedAccess_.name), "Eve"));

        List<EmployeeMixedAccess> result = entityManager.createQuery(cq).getResultList();
        assertEquals(1, result.size());
        assertEquals("Eve", result.getFirst().getName());
        assertNotNull(result.getFirst().getId());
    }
}
