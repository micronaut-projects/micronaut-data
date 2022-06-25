/*
 * Copyright 2017-2021 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.repository.jpa.criteria;

import io.micronaut.core.annotation.NonNull;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;

/**
 * Query criteria builder specification.
 *
 * @param <R> The result type
 * @author Denis Stepanov
 * @since 3.5.0
 */
public interface CriteriaQueryBuilder<R> {

    /**
     * Creates a query.
     *
     * @param criteriaBuilder The criteria builder
     * @return a query
     */
    @NonNull
    CriteriaQuery<R> build(@NonNull CriteriaBuilder criteriaBuilder);

}
