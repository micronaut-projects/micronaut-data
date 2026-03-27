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

import com.example.Child;
import com.example.Child_;
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;

public class ChildSpecification {

    public static PredicateSpecification<Child> idEquals(Long id) {
        return (root, criteriaBuilder) -> criteriaBuilder.equal(root.get(Child_.id), id);
    }

    public static PredicateSpecification<Child> nameEquals(String name) {
        return (root, criteriaBuilder) -> criteriaBuilder.equal(root.get(Child_.name), name);
    }

    public static PredicateSpecification<Child> ageGreaterThan(Long age) {
        return (root, criteriaBuilder) -> criteriaBuilder.greaterThan(root.get(Child_.age), age);
    }

}
