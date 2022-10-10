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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.naming.NamingStrategies;
import io.micronaut.data.model.naming.NamingStrategy;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.query.builder.QueryParameterBinding;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.Function;

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
        super();
    }

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

    @Override
    protected String getMappedName(NamingStrategy namingStrategy, PersistentProperty property) {
        ArgumentUtils.requireNonNull("property", property);
        if (property instanceof Association) {
            return getMappedName(namingStrategy, (Association) property);
        } else {
            AnnotationMetadata propertyAnnotationMetadata = property.getAnnotationMetadata();
            boolean generated = propertyAnnotationMetadata.booleanValue(MappedProperty.class, "generated").orElse(false);
            return propertyAnnotationMetadata
                .stringValue(MappedProperty.class)
                .filter(n -> !generated && StringUtils.isNotEmpty(n))
                .orElseGet(() -> namingStrategy.mappedName(property.getName()));
        }
    }

    @Override
    protected String getMappedName(NamingStrategy namingStrategy, Association association) {
        AnnotationMetadata assocationAnnotationMetadata = association.getAnnotationMetadata();
        boolean generated = assocationAnnotationMetadata.booleanValue(MappedProperty.class, "generated").orElse(false);
        String providedName = generated ? null : assocationAnnotationMetadata.stringValue(MappedProperty.class).orElse(null);
        if (providedName != null) {
            return providedName;
        }
        if (association.isForeignKey()) {
            Optional<Association> inverseSide = association.getInverseSide().map(Function.identity());
            Association owningAssociation = inverseSide.orElse(association);
            return namingStrategy.mappedName(owningAssociation.getOwner().getDecapitalizedName() + owningAssociation.getAssociatedEntity().getSimpleName());
        } else {
            switch (association.getKind()) {
                case ONE_TO_ONE:
                case MANY_TO_ONE:
                    return namingStrategy.mappedName(association.getName() + namingStrategy.getForeignKeySuffix());
                default:
                    return namingStrategy.mappedName(association.getName());
            }
        }
    }

    @Override
    protected String getMappedName(NamingStrategy namingStrategy, List<Association> associations, PersistentProperty property) {
        if (associations.isEmpty()) {
            return getMappedName(namingStrategy, property);
        }
        StringBuilder sb = new StringBuilder();
        Association foreignAssociation = null;
        for (Association association : associations) {
            if (association.getKind() != Relation.Kind.EMBEDDED) {
                if (foreignAssociation == null) {
                    foreignAssociation = association;
                }
            }
            if (sb.length() > 0) {
                sb.append(NameUtils.capitalize(association.getName()));
            } else {
                sb.append(association.getName());
            }
        }
        if (foreignAssociation != null) {
            if (foreignAssociation.getAssociatedEntity() == property.getOwner()
                && foreignAssociation.getAssociatedEntity().getIdentity() == property) {
                AnnotationMetadata foreignAssociationAnnotationMetadata = foreignAssociation.getAnnotationMetadata();
                boolean generated = foreignAssociationAnnotationMetadata.booleanValue(MappedProperty.class, "generated").orElse(false);
                String providedName = generated ? null : foreignAssociationAnnotationMetadata.stringValue(MappedProperty.class).orElse(null);
                if (providedName != null) {
                    return providedName;
                }
                sb.append(namingStrategy.getForeignKeySuffix());
                return namingStrategy.mappedName(sb.toString());
            } else if (foreignAssociation.isForeignKey()) {
                throw new IllegalStateException("Foreign association cannot be mapped!");
            }
        } else {
            AnnotationMetadata propertyAnnotationMetadata = property.getAnnotationMetadata();
            boolean generated = propertyAnnotationMetadata.booleanValue(MappedProperty.class, "generated").orElse(false);
            String providedName = generated ? null : propertyAnnotationMetadata.stringValue(MappedProperty.class).orElse(null);
            if (providedName != null) {
                return providedName;
            }
        }
        if (sb.length() > 0) {
            sb.append(NameUtils.capitalize(property.getName()));
        } else {
            sb.append(property.getName());
        }
        return namingStrategy.mappedName(sb.toString());
    }

    @Override
    protected StringBuilder appendDeleteClause(StringBuilder queryString) {
        // For delete we return SELECT * FROM ... WHERE to get documents and use API to delete them
        return queryString.append("SELECT * ").append(FROM_CLAUSE);
    }

    @Override
    protected boolean isAliasForBatch() {
        return true;
    }

    @Override
    public QueryResult buildInsert(AnnotationMetadata repositoryMetadata, PersistentEntity entity) {
        return null;
    }

    @Override
    public QueryResult buildUpdate(AnnotationMetadata annotationMetadata, QueryModel query, Map<String, Object> propertiesToUpdate) {
        QueryResult queryResult = super.buildUpdate(annotationMetadata, query, propertiesToUpdate);
        String resultQuery = queryResult.getQuery();

        PersistentEntity entity = query.getPersistentEntity();
        String tableAlias = getAliasName(entity);
        String tableName = getTableName(entity);

        final String finalQuery = new StringBuilder("SELECT * FROM ").append(tableName).append(SPACE).append(tableAlias).append(SPACE)
            .append(resultQuery.substring(resultQuery.toLowerCase(Locale.ROOT).indexOf("where"))).toString();
        StringJoiner stringJoiner = new StringJoiner(",");
        propertiesToUpdate.keySet().forEach(s -> stringJoiner.add(s));
        final String update = stringJoiner.toString();

        return new QueryResult() {

            @NonNull
            @Override
            public String getQuery() {
                return finalQuery;
            }

            @Override
            public String getUpdate() {
                return update;
            }

            @Override
            public List<String> getQueryParts() {
                return queryResult.getQueryParts();
            }

            @Override
            public List<QueryParameterBinding> getParameterBindings() {
                return queryResult.getParameterBindings();
            }

            @Override
            public Map<String, String> getAdditionalRequiredParameters() {
                return queryResult.getAdditionalRequiredParameters();
            }

        };
    }
}
