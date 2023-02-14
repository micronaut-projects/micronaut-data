/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.data.document.model.query.builder;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.naming.NamingStrategies;
import io.micronaut.data.model.naming.NamingStrategy;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;

import java.util.List;

/**
 * The AWS DynamoDB sql query builder.
 *
 * @author radovanradic
 * @since 4.0.0
 */
public class DynamoDbSqlQueryBuilder extends SqlQueryBuilder {

    private static final String USE_INDEX_ANNOTATION = "io.micronaut.data.aws.dynamodb.annotation.UseIndex";
    private static final NamingStrategy RAW_NAMING_STRATEGY = new NamingStrategies.Raw();

    @Creator
    public DynamoDbSqlQueryBuilder(AnnotationMetadata annotationMetadata) {
        super(annotationMetadata);
        initializeCriteriaHandlers();
    }

    /**
     * Default constructor.
     */
    public DynamoDbSqlQueryBuilder() {
        super();
        initializeCriteriaHandlers();
    }

    @Override
    protected NamingStrategy getNamingStrategy(PersistentEntity entity) {
        return entity.findNamingStrategy().orElse(RAW_NAMING_STRATEGY);
    }

    @Override
    protected NamingStrategy getNamingStrategy(PersistentPropertyPath propertyPath) {
        return propertyPath.findNamingStrategy().orElse(RAW_NAMING_STRATEGY);
    }

    @Override
    protected String getAliasName(PersistentEntity entity) {
        return null;
    }

    @Override
    protected void buildJoin(String joinType,
                             StringBuilder sb,
                             QueryState queryState,
                             List<Association> joinAssociationsPath,
                             String joinAlias,
                             Association association,
                             PersistentEntity associatedEntity,
                             PersistentEntity associationOwner,
                             String currentJoinAlias) {
        throw new IllegalStateException("Joins are not supported in AWS DynamoDB Micronaut Data");
    }

    @Override
    protected void appendSelectFromClause(AnnotationMetadata annotationMetadata, String tableName, String logicalName, StringBuilder queryString) {
        queryString.append(FROM_CLAUSE).append(tableName);
        if (annotationMetadata.hasStereotype(USE_INDEX_ANNOTATION)) {
            String indexName = annotationMetadata.getAnnotation(USE_INDEX_ANNOTATION).stringValue().orElse(null);
            if (StringUtils.isNotEmpty(indexName)) {
                queryString.append(DOT).append(quote(indexName));
            }
        }
    }

    /**
     * Initializes criteria handlers specific for Cosmos Db.
     */
    private void initializeCriteriaHandlers() {
        // TODO: Implement custom handlers
    }
}
