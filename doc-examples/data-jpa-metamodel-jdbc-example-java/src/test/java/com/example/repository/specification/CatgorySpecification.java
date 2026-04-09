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
import io.micronaut.data.repository.jpa.criteria.QuerySpecification;
import io.micronaut.entities.Book_;
import io.micronaut.entities.Category;
import io.micronaut.entities.Category_;
import jakarta.persistence.criteria.JoinType;

public class CatgorySpecification {

    public static PredicateSpecification<Category> idEquals(Long id) {
        return (root, criteriaBuilder) -> criteriaBuilder.equal(root.get(Category_.id), id);
    }

    public static PredicateSpecification<Category> nameEquals(String name) {
        return (root, criteriaBuilder) -> criteriaBuilder.equal(root.get(Category_.name), name);
    }

    public static PredicateSpecification<Category> bytesNotNull() {
        return (root, criteriaBuilder) -> criteriaBuilder.isNotNull(root.get(Category_.bytes));
    }

    public static QuerySpecification<Category> withBooksTitleEquals(String title) {
        return (root, query, cb) -> {
            var books = root.join(Category_.books, JoinType.INNER);
            return cb.equal(books.get(Book_.title), title);
        };
    }

    public static QuerySpecification<Category> withBooks() {
        return (root, query, cb) -> {
            var books = root.join(Category_.books, JoinType.LEFT);
            return cb.isNotNull(books.get(Book_.id));
        };
    }
}
