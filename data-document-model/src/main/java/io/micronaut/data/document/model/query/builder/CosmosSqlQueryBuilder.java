/*
 * Copyright 2017-2022 original authors
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
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.naming.NamingStrategies;
import io.micronaut.data.model.naming.NamingStrategy;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;

/**
 * The Azure Cosmos DB sql query builder.
 *
 * @since 3.8.0
 */
public final class CosmosSqlQueryBuilder extends SqlQueryBuilder {

    private static final String SELECT_COUNT = "VALUE COUNT(1)";

    @Creator
    public CosmosSqlQueryBuilder(AnnotationMetadata annotationMetadata) {
        super(annotationMetadata);
    }

    /**
     * Default constructor.
     */
    public CosmosSqlQueryBuilder() {
        super(); }

    @Override
    protected String asLiteral(Object value) {
        if (value instanceof Boolean) {
            return value.toString();
        }
        return super.asLiteral(value);
    }

    @Override
    protected void appendProjectionRowCount(StringBuilder queryString, String logicalName) {
        queryString.append(SELECT_COUNT);
    }

    @Override
    protected NamingStrategy getNamingStrategy(PersistentEntity entity) {
        return entity.findNamingStrategy().orElseGet(NamingStrategies.Raw::new); // Make a constant?
    }

    @Override
    protected NamingStrategy getNamingStrategy(PersistentPropertyPath propertyPath) {
        return propertyPath.findNamingStrategy().orElseGet(NamingStrategies.Raw::new);
    }
}
