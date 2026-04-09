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
import io.micronaut.entities.Book_;
import io.micronaut.entities.Review;
import io.micronaut.entities.Review_;
import jakarta.persistence.criteria.JoinType;

public class ReviewSpecification {
    public static PredicateSpecification<Review> withBookTitleEquals(String title) {
        return (root, cb) -> {
            var book = root.join(Review_.book, JoinType.RIGHT);
            return cb.equal(book.get(Book_.title), title);
        };
    }

    public static PredicateSpecification<Review> withReviewerEqual(String name) {
        return (root, cb) -> cb.equal(root.get(Review_.reviewer), name);
    }

}
