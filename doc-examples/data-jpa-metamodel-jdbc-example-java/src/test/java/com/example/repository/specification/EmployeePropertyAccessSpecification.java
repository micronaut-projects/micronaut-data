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

package com.example.repository.specification;

import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;
import io.micronaut.entities.EmployeePropertyAccess;
import io.micronaut.entities.EmployeePropertyAccess_;

public class EmployeePropertyAccessSpecification {

    public static PredicateSpecification<EmployeePropertyAccess> nameEquals(String name) {
        return (root, criteriaBuilder) -> criteriaBuilder.equal(root.get(EmployeePropertyAccess_.name), name);
    }

    public static PredicateSpecification<EmployeePropertyAccess> salaryBiggerThan(Double salary) {
        return (root, criteriaBuilder) -> criteriaBuilder.greaterThan(root.get(EmployeePropertyAccess_.salary), salary);
    }
}
