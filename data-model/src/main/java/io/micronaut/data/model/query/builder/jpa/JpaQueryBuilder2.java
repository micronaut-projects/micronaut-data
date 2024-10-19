/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.data.model.query.builder.jpa;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.Embedded;
import io.micronaut.data.model.PersistentAssociationPath;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.naming.NamingStrategy;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.model.query.builder.sql.AbstractSqlLikeQueryBuilder2;
import io.micronaut.data.model.query.builder.sql.Dialect;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds JPA 1.0 String-based queries from the Query model.
 *
 * @author Graeme Rocher
 * @author Denis Stepanov
 * @since 1.0
 */
@Internal
public final class JpaQueryBuilder2 extends AbstractSqlLikeQueryBuilder2 {

    private static final NamingStrategy JPA_NAMING_STRATEGY = new NamingStrategy() {
        @Override
        public String mappedName(String name) {
            return name;
        }

        @Override
        public String mappedAssociatedName(String associatedName) {
            return associatedName;
        }

        @Override
        public String mappedName(Association association) {
            String providedName = association.getAnnotationMetadata().stringValue(MappedProperty.class).orElse(null);
            if (providedName != null) {
                return providedName;
            }
            return association.getName();
        }
    };

    /**
     * Default constructor.
     */
    public JpaQueryBuilder2() {
    }

    @Override
    protected String quote(String persistedName) {
        return persistedName;
    }

    @Override
    public String getAliasName(PersistentEntity entity) {
        return entity.getAnnotationMetadata().stringValue(MappedEntity.class, "alias")
                .orElseGet(() -> entity.getDecapitalizedName() + "_");
    }

    @Override
    protected void buildJoin(String joinType,
                             StringBuilder query,
                             QueryState queryState,
                             PersistentAssociationPath joinAssociation,
                             PersistentEntity associationOwner,
                             String currentJoinAlias,
                             String lastJoinAlias) {
        query.append(joinType)
            .append(lastJoinAlias).append(DOT)
            .append(joinAssociation.getAssociation().getName())
            .append(SPACE)
            .append(currentJoinAlias);
    }

    @Override
    protected String buildAdditionalWhereClause(QueryState queryState, AnnotationMetadata annotationMetadata) {
        StringBuilder additionalWhereBuff = new StringBuilder(buildAdditionalWhereString(queryState.getRootAlias(), queryState.getEntity(), annotationMetadata));
        List<JoinPath> joinPaths = queryState.getJoinPaths();
        if (CollectionUtils.isNotEmpty(joinPaths)) {
            Set<String> addedJoinPaths = new HashSet<>();
            for (JoinPath joinPath : joinPaths) {
                String path = joinPath.getPath();
                if (addedJoinPaths.contains(path)) {
                    continue;
                }
                addedJoinPaths.add(path);
                String joinAdditionalWhere = buildAdditionalWhereString(joinPath, annotationMetadata);
                if (StringUtils.isNotEmpty(joinAdditionalWhere)) {
                    if (!additionalWhereBuff.isEmpty()) {
                        additionalWhereBuff.append(SPACE).append(AND).append(SPACE);
                    }
                    additionalWhereBuff.append(joinAdditionalWhere);
                }
            }
        }
        return additionalWhereBuff.toString();
    }

    @Override
    protected String getTableName(PersistentEntity entity) {
        return entity.getName();
    }

    @Override
    protected String getColumnName(PersistentProperty persistentProperty) {
        return persistentProperty.getName();
    }

    @Override
    protected SqlSelectionVisitor createSelectionVisitor(AnnotationMetadata annotationMetadata, QueryState queryState, boolean distinct) {
        return new SqlSelectionVisitor(queryState, annotationMetadata, distinct) {

            @Override
            protected void selectAllColumnsAndJoined() {
                query.append(tableAlias);
            }

            @Override
            protected void selectAllColumns(AnnotationMetadata annotationMetadata, PersistentEntity persistentEntity, String tableAlias) {
                query.append(tableAlias);
            }

            @Override
            protected void appendRowCount(String logicalName) {
                query.append("COUNT")
                    .append(OPEN_BRACKET)
                    .append(logicalName)
                    .append(CLOSE_BRACKET);
            }

            @Override
            protected void appendRowCountDistinct(String logicalName) {
                query.append("COUNT(DISTINCT")
                    .append(OPEN_BRACKET)
                    .append(logicalName)
                    .append(CLOSE_BRACKET)
                    .append(CLOSE_BRACKET);
            }

            @Override
            protected void appendCompoundAssociationProjection(PersistentAssociationPath propertyPath) {
                String joinAlias = queryState.getJoinAlias(propertyPath.getPath());
                query.append(joinAlias).append(AS_CLAUSE).append(columnAlias != null ? columnAlias : propertyPath.getProperty().getName());
            }

            @Override
            protected void appendCompoundPropertyProjection(PersistentPropertyPath propertyPath) {
                PersistentProperty property = propertyPath.getProperty();
                if (property instanceof Embedded) {
                    query.append(tableAlias).append(DOT).append(propertyPath.getPath());
                    if (columnAlias != null) {
                        query.append(AS_CLAUSE).append(columnAlias);
                    }
                    return;
                }
                super.appendCompoundPropertyProjection(propertyPath);
            }

        };
    }

    @Override
    protected boolean computePropertyPaths() {
        return false;
    }

    @Override
    protected boolean isAliasForBatch(PersistentEntity persistentEntity, AnnotationMetadata annotationMetadata) {
        return true;
    }

    @Override
    protected Placeholder formatParameter(int index) {
        String n = "p" + index;
        return new Placeholder(":" + n, n);
    }

    @Override
    public String resolveJoinType(Join.Type jt) {
        return switch (jt) {
            case LEFT -> " LEFT JOIN ";
            case LEFT_FETCH -> " LEFT JOIN FETCH ";
            case RIGHT -> " RIGHT JOIN ";
            case RIGHT_FETCH -> " RIGHT JOIN FETCH ";
            case INNER, FETCH -> " JOIN FETCH ";
            default -> " JOIN ";
        };
    }

    @Override
    public QueryResult buildInsert(AnnotationMetadata repositoryMetadata, InsertQueryDefinition definition) {
        // JPA doesn't require an insert statement
        return null;
    }

    @NonNull
    @Override
    protected StringBuilder appendDeleteClause(StringBuilder queryString) {
        return queryString.append("DELETE ");
    }

    @Override
    public String buildLimitAndOffset(long limit, long offset) {
        throw new UnsupportedOperationException("JPA-QL does not support pagination in query definitions");
    }

    @Override
    protected void appendLimitAndOffset(Dialect dialect, long limit, long offset, StringBuilder builder) {
        // JPA doesn't support limit and offset in JPQL
    }

    @Override
    protected void appendPaginationAndOrder(AnnotationMetadata annotationMetadata, SelectQueryDefinition definition, boolean pagination, QueryState queryState) {
        appendOrder(annotationMetadata, definition.order(), queryState);
    }

    @Override
    protected NamingStrategy getNamingStrategy(PersistentEntity entity) {
        return JPA_NAMING_STRATEGY;
    }

    @Override
    protected NamingStrategy getNamingStrategy(PersistentPropertyPath propertyPath) {
        return JPA_NAMING_STRATEGY;
    }

}
