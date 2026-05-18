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
package io.micronaut.data.model.jpa.criteria;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryResult;
import org.jspecify.annotations.Nullable;

/**
 * The query builder.
 *
 * @author Denis Stepanov
 * @since 5.0
 */
@Experimental
public interface PersistentEntityCriteriaQueryBuilder {

    /**
     * Build the query.
     *
     * @param annotationMetadata The annotation metadata.
     * @param queryBuilder       The query builder
     * @return The query result
     */
    @Nullable
    QueryResult build(AnnotationMetadata annotationMetadata,  QueryBuilder queryBuilder);

    /**
     * Build the query.
     *
     * @param queryBuilder The query builder
     * @return The query result
     */
    @Nullable
    default QueryResult build(QueryBuilder queryBuilder) {
        return build(AnnotationMetadata.EMPTY_METADATA, queryBuilder);
    }

}
