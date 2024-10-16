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
package io.micronaut.data.model.jpa.criteria.impl;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryBuilder2;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.model.query.builder.jpa.JpaQueryBuilder;
import io.micronaut.data.model.query.builder.jpa.JpaQueryBuilder2;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder2;

/**
 * The query provider.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public interface QueryResultPersistentEntityCriteriaQuery {

    default QueryResult buildQuery(AnnotationMetadata annotationMetadata, QueryBuilder queryBuilder) {
        return buildQuery(annotationMetadata, asQueryBuilder2(queryBuilder));
    }

    QueryResult buildQuery(AnnotationMetadata annotationMetadata, QueryBuilder2 queryBuilder);

    @NonNull
    private static QueryBuilder2 asQueryBuilder2(QueryBuilder queryBuilder) {
        Class<? extends QueryBuilder> queryBuilderClass = queryBuilder.getClass();
        if (queryBuilderClass.getSimpleName().equals("CosmosSqlQueryBuilder")) {
            // Use new implementation
            try {
                return (QueryBuilder2) queryBuilderClass
                    .getClassLoader().loadClass("io.micronaut.data.document.model.query.builder.CosmosSqlQueryBuilder2")
                    .getDeclaredConstructor(AnnotationMetadata.class)
                    .newInstance(((SqlQueryBuilder) queryBuilder).getAnnotationMetadata());
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        if (queryBuilderClass.getSimpleName().equals("MongoQueryBuilder")) {
            // Use new implementation
            try {
                return (QueryBuilder2) queryBuilderClass
                    .getClassLoader().loadClass("io.micronaut.data.document.model.query.builder.MongoQueryBuilder2")
                    .getDeclaredConstructor()
                    .newInstance();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        if (queryBuilderClass == SqlQueryBuilder.class) {
            // Use new implementation
            AnnotationMetadata builderAnnotationMetadata = ((SqlQueryBuilder) queryBuilder).getAnnotationMetadata();
            if (builderAnnotationMetadata == null) {
                return new SqlQueryBuilder2(((SqlQueryBuilder) queryBuilder).getDialect());
            } else {
                return new SqlQueryBuilder2(builderAnnotationMetadata);
            }
        }
        if (queryBuilderClass == JpaQueryBuilder.class) {
            // Use new implementation
            return new JpaQueryBuilder2();
        }
        return new LegacyQueryModelQueryBuilder(queryBuilder);
    }

}
