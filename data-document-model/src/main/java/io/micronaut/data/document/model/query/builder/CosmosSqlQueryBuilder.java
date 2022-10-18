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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.repeatable.WhereSpecifications;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.Embedded;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.naming.NamingStrategies;
import io.micronaut.data.model.naming.NamingStrategy;
import io.micronaut.data.model.query.BindingParameter;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.query.builder.QueryParameterBinding;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * The Azure Cosmos DB sql query builder.
 *
 * @author radovanradic
 * @since 4.0.0
 */
public final class CosmosSqlQueryBuilder extends SqlQueryBuilder {

    private static final String SELECT_COUNT = "VALUE COUNT(1)";
    private static final String JOIN = " JOIN ";
    private static final String IN = " IN ";

    {
        addCriterionHandler(QueryModel.In.class, inCriterionHandler(false));
        addCriterionHandler(QueryModel.NotIn.class, inCriterionHandler(true));
    }

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
                sb.append(DOT).append(association.getName());
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
            sb.append(DOT).append(property.getName());
        } else {
            sb.append(property.getName());
        }
        return namingStrategy.mappedName(sb.toString());
    }

    @Override
    protected void traversePersistentProperties(List<Association> associations,
                                                PersistentProperty property,
                                                boolean criteria,
                                                BiConsumer<List<Association>, PersistentProperty> consumerProperty) {
        if (property instanceof Embedded && !criteria) {
            consumerProperty.accept(associations, property);
            return;
        }
        super.traversePersistentProperties(associations, property, criteria, consumerProperty);
    }

    private <T extends QueryModel.PropertyCriterion> CriterionHandler<T> inCriterionHandler(boolean negate) {
        return (ctx, inQuery) -> {
            QueryPropertyPath propertyPath = ctx.getRequiredProperty(inQuery.getProperty(), negate ? QueryModel.NotIn.class : QueryModel.In.class);
            StringBuilder whereClause = ctx.query();
            if (negate) {
                whereClause.append(NOT);
            }
            whereClause.append(" ARRAY_CONTAINS(");
            Object value = inQuery.getValue();
            if (value instanceof BindingParameter) {
                ctx.pushParameter((BindingParameter) value, newBindingContext(propertyPath.getPropertyPath()).expandable());
            } else {
                asLiterals(ctx.query(), value);
            }
            whereClause.append(COMMA);
            appendPropertyRef(whereClause, propertyPath);
            whereClause.append(CLOSE_BRACKET);
        };
    }

    @Override
    public QueryResult buildQuery(@NonNull AnnotationMetadata annotationMetadata, @NonNull QueryModel query) {
        ArgumentUtils.requireNonNull("annotationMetadata", annotationMetadata);
        ArgumentUtils.requireNonNull("query", query);
        QueryState queryState = new QueryState(query, true, true);

        List<JoinPath> joinPaths = new ArrayList<>(query.getJoinPaths());
        joinPaths.sort((o1, o2) -> Comparator.comparingInt(String::length).thenComparing(String::compareTo).compare(o1.getPath(), o2.getPath()));
        for (JoinPath joinPath : joinPaths) {
            queryState.applyJoin(joinPath);
        }

        StringBuilder select = new StringBuilder(SELECT_CLAUSE);
        String logicalName = queryState.getRootAlias();
        PersistentEntity entity = queryState.getEntity();
        buildSelect(
            queryState,
            select,
            query.getProjections(),
            logicalName,
            entity
        );

        select.append(FROM_CLAUSE).append(getTableName(entity)).append(SPACE).append(logicalName);

        QueryModel queryModel = queryState.getQueryModel();
        Collection<JoinPath> allPaths = queryModel.getJoinPaths();
        appendJoins(queryState, select, allPaths, null);

        queryState.getQuery().insert(0, select);

        QueryModel.Junction criteria = query.getCriteria();

        if (!criteria.isEmpty() || annotationMetadata.hasStereotype(WhereSpecifications.class) || queryState.getEntity().getAnnotationMetadata().hasStereotype(WhereSpecifications.class)) {
            buildWhereClause(annotationMetadata, criteria, queryState);
        }

        appendOrder(query, queryState);
        appendForUpdate(QueryPosition.END_OF_QUERY, query, queryState.getQuery());

        return QueryResult.of(
            queryState.getFinalQuery(),
            queryState.getQueryParts(),
            queryState.getParameterBindings(),
            queryState.getAdditionalRequiredParameters(),
            query.getMax(),
            query.getOffset(),
            queryState.getJoinPaths()
        );
    }

    @Internal
    @Override
    protected void selectAllColumnsFromJoinPaths(QueryState queryState,
                                                 StringBuilder queryBuffer,
                                                 Collection<JoinPath> allPaths,
                                                 @Nullable Map<JoinPath, String> joinAliasOverride) {
        // Does nothing since we don't select columns in joins
    }

    /**
     * We use this method instead of {@link #selectAllColumnsFromJoinPaths(QueryState, StringBuilder, Collection, Map)}
     * and said method is empty because Cosmos Db has different join logic.
     * @param queryState
     * @param queryBuffer
     * @param allPaths
     * @param joinAliasOverride
     */
    private void appendJoins(QueryState queryState,
                                                 StringBuilder queryBuffer,
                                                 Collection<JoinPath> allPaths,
                                                 @Nullable Map<JoinPath, String> joinAliasOverride) {
        String logicalName = queryState.getRootAlias();
        if (CollectionUtils.isNotEmpty(allPaths)) {
            Map<String, String> joinedPaths = new HashMap<>();
            for (JoinPath joinPath : allPaths) {
                Association association = joinPath.getAssociation();
                if (association instanceof Embedded) {
                    // joins on embedded don't make sense
                    continue;
                }
                String joinAlias = joinAliasOverride == null ? getAliasName(joinPath) : joinAliasOverride.get(joinPath);
                // cannot join family_.children c join family_children.pets p but instead must do
                // join family_.children c join c.pets p (must go via children table)
                String path = new StringBuilder(logicalName).append(DOT).append(joinPath.getPath()).toString();
                for (Map.Entry<String, String> entry : joinedPaths.entrySet()) {
                    String joinedPath = entry.getKey();
                    String prefix = joinedPath + DOT;
                    if (path.startsWith(prefix) && !joinedPath.equals(path)) {
                        path = entry.getValue() + DOT + path.replace(prefix, "");
                        break;
                    }
                }
                queryBuffer.append(JOIN).append(joinAlias).append(IN).append(path);
                joinedPaths.put(path, joinAlias);
            }
        }
    }

    @Override
    protected boolean appendAssociationProjection(QueryState queryState, StringBuilder queryString, PersistentProperty property, PersistentPropertyPath propertyPath) {
        String joinedPath = propertyPath.getPath();
        if (!queryState.isJoined(joinedPath)) {
            queryString.setLength(queryString.length() - 1);
            return false;
        }
        String joinAlias = queryState.computeAlias(propertyPath.getPath());
        selectAllColumns(((Association) property).getAssociatedEntity(), joinAlias, queryString);
        return true;
    }

    @Override
    protected void selectAllColumns(QueryState queryState, StringBuilder queryBuffer) {
        queryBuffer.append("DISTINCT VALUE ").append(queryState.getRootAlias());
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
        // Does nothing since joins in Cosmos Db work different way
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
