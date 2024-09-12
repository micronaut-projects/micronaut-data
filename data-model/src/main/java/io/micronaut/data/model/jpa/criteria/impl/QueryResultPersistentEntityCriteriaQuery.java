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
import io.micronaut.data.annotation.Join;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.query.QueryModel;
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

    static QueryBuilder2 findQueryBuilder2(QueryBuilder queryBuilder) {
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
                return (QueryBuilder2)  queryBuilderClass
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
            return newSqlQueryBuilder((SqlQueryBuilder) queryBuilder);
        }
        if (queryBuilderClass == JpaQueryBuilder.class) {
            // Use new implementation
            return new JpaQueryBuilder2();
        }
        return null;
    }

    QueryResult buildQuery(AnnotationMetadata annotationMetadata, QueryBuilder queryBuilder);

    QueryModel getQueryModel();

    default QueryResult buildCountQuery(AnnotationMetadata annotationMetadata, QueryBuilder queryBuilder) {
        if (queryBuilder.getClass() == SqlQueryBuilder.class) {
            // Use new implementation
            return buildCountQuery(annotationMetadata, newSqlQueryBuilder((SqlQueryBuilder) queryBuilder));
        }
        if (queryBuilder.getClass() == JpaQueryBuilder.class) {
            // Use new implementation
            return buildCountQuery(annotationMetadata, new JpaQueryBuilder2());
        }
        QueryModel queryModel = getQueryModel();
        QueryModel countQuery = QueryModel.from(queryModel.getPersistentEntity());
        countQuery.projections().count();
        QueryModel.Junction junction = queryModel.getCriteria();
        for (QueryModel.Criterion criterion : junction.getCriteria()) {
            countQuery.add(criterion);
        }

        for (JoinPath joinPath : queryModel.getJoinPaths()) {
            Join.Type joinType = joinPath.getJoinType();
            switch (joinType) {
                case INNER:
                case FETCH:
                    joinType = Join.Type.DEFAULT;
                    break;
                case LEFT_FETCH:
                    joinType = Join.Type.LEFT;
                    break;
                case RIGHT_FETCH:
                    joinType = Join.Type.RIGHT;
                    break;
                default:
                    // no-op
            }
            countQuery.join(joinPath.getPath(), joinType, null);
        }
        return queryBuilder.buildQuery(annotationMetadata, countQuery);
    }

    QueryResult buildQuery(AnnotationMetadata annotationMetadata, QueryBuilder2 queryBuilder);

    default QueryResult buildCountQuery(AnnotationMetadata annotationMetadata, QueryBuilder2 queryBuilder) {
        throw new UnsupportedOperationException();
    }

    private static QueryBuilder2 newSqlQueryBuilder(SqlQueryBuilder sqlQueryBuilder) {
        // Use new implementation
        AnnotationMetadata builderAnnotationMetadata = sqlQueryBuilder.getAnnotationMetadata();
        if (builderAnnotationMetadata == null) {
            return new SqlQueryBuilder2(sqlQueryBuilder.getDialect());
        } else {
            return new SqlQueryBuilder2(builderAnnotationMetadata);
        }
    }

}
