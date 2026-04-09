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
import io.micronaut.entities.Book;
import io.micronaut.entities.Book_;
import io.micronaut.entities.Category;
import io.micronaut.entities.Category_;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;

public class BookSpecification {

    public static PredicateSpecification<Book> titleEquals(String title) {
        return (root, criteriaBuilder) -> criteriaBuilder.equal(root.get(Book_.title), title);
    }

    public static PredicateSpecification<Book> pagesGreaterThanOrEqualTo(int pages) {
        return (root, criteriaBuilder) -> criteriaBuilder.greaterThanOrEqualTo(root.get(Book_.pages), pages);
    }

    public static PredicateSpecification<Book> pagesLessThan(int pages) {
        return (root, criteriaBuilder) -> criteriaBuilder.lessThan(root.get(Book_.pages), pages);
    }

    public static QuerySpecification<Book> withCategoryName(String name) {
        return (root, query, criteriaBuilder) -> {
            Join<Book, Category> categoryJoin = root.join(Book_.category, JoinType.RIGHT);
            return criteriaBuilder.equal(categoryJoin.get(Category_.name), name);
        };
    }

}
