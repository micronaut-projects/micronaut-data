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
import io.micronaut.data.model.Pageable;
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
import java.util.Collections;
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
 * @since 3.9.0
 */
public final class CosmosSqlQueryBuilder extends SqlQueryBuilder {

    private static final String GENERATED = "generated";
    private static final String VALUE = "VALUE ";
    private static final String SELECT_COUNT = "COUNT(1)";
    private static final String JOIN = " JOIN ";
    private static final String IN = " IN ";
    private static final String IS_NULL = "IS_NULL";
    private static final String IS_DEFINED = "IS_DEFINED";
    private static final String ARRAY_CONTAINS = "ARRAY_CONTAINS";

    private static final NamingStrategy RAW_NAMING_STRATEGY = new NamingStrategies.Raw();

    @Creator
    public CosmosSqlQueryBuilder(AnnotationMetadata annotationMetadata) {
        super(annotationMetadata);
        initializeCriteriaHandlers();
    }

    /**
     * Default constructor.
     */
    public CosmosSqlQueryBuilder() {
        super();
        initializeCriteriaHandlers();
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
        return entity.findNamingStrategy().orElse(RAW_NAMING_STRATEGY);
    }

    @Override
    protected NamingStrategy getNamingStrategy(PersistentPropertyPath propertyPath) {
        return propertyPath.findNamingStrategy().orElse(RAW_NAMING_STRATEGY);
    }

    @Override
    protected String getMappedName(NamingStrategy namingStrategy, PersistentProperty property) {
        ArgumentUtils.requireNonNull("property", property);
        if (property instanceof Association) {
            return getMappedName(namingStrategy, (Association) property);
        } else {
            AnnotationMetadata propertyAnnotationMetadata = property.getAnnotationMetadata();
            boolean generated = propertyAnnotationMetadata.booleanValue(MappedProperty.class, GENERATED).orElse(false);
            return propertyAnnotationMetadata
                .stringValue(MappedProperty.class)
                .filter(n -> !generated && StringUtils.isNotEmpty(n))
                .orElseGet(() -> namingStrategy.mappedName(property.getName()));
        }
    }

    @Override
    protected String getMappedName(NamingStrategy namingStrategy, Association association) {
        AnnotationMetadata assocationAnnotationMetadata = association.getAnnotationMetadata();
        boolean generated = assocationAnnotationMetadata.booleanValue(MappedProperty.class, GENERATED).orElse(false);
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
        Association foreignAssociation = getForeignAssociation(sb, associations);
        if (foreignAssociation != null) {
            if (foreignAssociation.getAssociatedEntity() == property.getOwner()
                && foreignAssociation.getAssociatedEntity().getIdentity() == property) {
                AnnotationMetadata foreignAssociationAnnotationMetadata = foreignAssociation.getAnnotationMetadata();
                String providedName = getProvidedName(foreignAssociationAnnotationMetadata);
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
            String providedName = getProvidedName(propertyAnnotationMetadata);
            if (providedName != null) {
                return providedName;
            }
        }
        appendProperty(sb, property);
        return namingStrategy.mappedName(sb.toString());
    }

    /**
     * Gets provided name from the property annotation metadata. If there was "generated" attribute returns
     * null meaning we want to recalculate field name, otherwise value of MappedProperty if defined or null if not defined.
     *
     * @param annotationMetadata the annotation metadata
     * @return the field name, will be null if generated by the mapped entity visitor
     */
    private String getProvidedName(AnnotationMetadata annotationMetadata) {
        boolean generated = annotationMetadata.booleanValue(MappedProperty.class, GENERATED).orElse(false);
        return generated ? null : annotationMetadata.stringValue(MappedProperty.class).orElse(null);
    }

    /**
     * Finds foreign association in list of associations.
     * @param sb the string builder
     * @param associations the list of associations
     * @return foreign association if present in the list of associations
     */
    private Association getForeignAssociation(StringBuilder sb, List<Association> associations) {
        Association foreignAssociation = null;
        for (Association association : associations) {
            if (association.getKind() != Relation.Kind.EMBEDDED && foreignAssociation == null) {
                foreignAssociation = association;
            }
            appendProperty(sb, association);
        }
        return foreignAssociation;
    }

    /**
     * Appends property name to the string builder, preceding with dot if prefix has been added.
     *
     * @param sb the string builder
     * @param property the persistent property
     */
    private void appendProperty(StringBuilder sb, PersistentProperty property) {
        if (sb.length() > 0) {
            sb.append(DOT).append(property.getName());
        } else {
            sb.append(property.getName());
        }
    }

    @Override
    protected void traversePersistentProperties(List<Association> associations,
                                                PersistentProperty property,
                                                BiConsumer<List<Association>, PersistentProperty> consumerProperty) {
        if (property instanceof Embedded) {
            consumerProperty.accept(associations, property);
            return;
        }
        super.traversePersistentProperties(associations, property, consumerProperty);
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
        List<QueryModel.Projection> projections = query.getProjections();
        buildSelect(
            queryState,
            select,
            projections,
            logicalName,
            entity
        );

        // For projections, we need to have VALUE in order to be able to read value
        // but for DTO when there can be more fields retrieved (meaning there is comma in the query) then VALUE cannot work
        // also literal projection does not need VALUE
        if (projections.size() == 1 && !(projections.get(0) instanceof QueryModel.LiteralProjection) && select.indexOf(",") == -1) {
            select.insert(SELECT_CLAUSE.length(), VALUE);
        }

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
        if (CollectionUtils.isEmpty(allPaths)) {
            return;
        }
        String logicalName = queryState.getRootAlias();
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
            String path = logicalName + DOT + joinPath.getPath();
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
        queryBuffer.append(DISTINCT).append(SPACE).append(VALUE).append(queryState.getRootAlias());
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
    protected boolean computePropertyPaths() {
        return false;
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

        final String finalQuery = "SELECT * FROM " + tableName + SPACE + tableAlias + SPACE +
            resultQuery.substring(resultQuery.toLowerCase(Locale.ROOT).indexOf("where"));
        StringJoiner stringJoiner = new StringJoiner(",");
        propertiesToUpdate.keySet().forEach(stringJoiner::add);
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

    @NonNull
    @Override
    public QueryResult buildPagination(@NonNull Pageable pageable) {
        int size = pageable.getSize();
        if (size > 0) {
            StringBuilder builder = new StringBuilder(" ");
            long from = pageable.getOffset();
            builder.append("OFFSET ").append(from).append(" LIMIT ").append(size).append(" ");
            return QueryResult.of(
                builder.toString(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyMap()
            );
        }
        return QueryResult.of(
            "",
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyMap()
        );
    }

    /**
     * Initializes criteria handlers specific for Cosmos Db.
     */
    private void initializeCriteriaHandlers() {
        addCriterionHandler(QueryModel.IsNull.class, (ctx, criterion) -> {
            ctx.query().append(NOT).append(SPACE).append(IS_DEFINED).append(OPEN_BRACKET);
            appendPropertyRef(ctx.query(), ctx.getRequiredProperty(criterion));
            ctx.query().append(CLOSE_BRACKET).append(SPACE).append(OR).append(SPACE);
            ctx.query().append(IS_NULL).append(OPEN_BRACKET);
            appendPropertyRef(ctx.query(), ctx.getRequiredProperty(criterion));
            ctx.query().append(CLOSE_BRACKET);
        });
        addCriterionHandler(QueryModel.IsNotNull.class, (ctx, criterion) -> {
            ctx.query().append(IS_DEFINED).append(OPEN_BRACKET);
            appendPropertyRef(ctx.query(), ctx.getRequiredProperty(criterion));
            ctx.query().append(CLOSE_BRACKET).append(SPACE).append(AND).append(SPACE);
            ctx.query().append(NOT).append(SPACE).append(IS_NULL).append(OPEN_BRACKET);
            appendPropertyRef(ctx.query(), ctx.getRequiredProperty(criterion));
            ctx.query().append(CLOSE_BRACKET);
        });
        addCriterionHandler(QueryModel.IsEmpty.class, (ctx, criterion) -> {
            ctx.query().append(NOT).append(SPACE).append(IS_DEFINED).append(OPEN_BRACKET);
            appendPropertyRef(ctx.query(), ctx.getRequiredProperty(criterion));
            ctx.query().append(CLOSE_BRACKET).append(SPACE).append(OR).append(SPACE);
            ctx.query().append(IS_NULL).append(OPEN_BRACKET);
            appendPropertyRef(ctx.query(), ctx.getRequiredProperty(criterion));
            ctx.query().append(CLOSE_BRACKET).append(SPACE).append(OR).append(SPACE);
            appendPropertyRef(ctx.query(), ctx.getRequiredProperty(criterion));
            ctx.query().append(EQUALS).append("''");
        });
        addCriterionHandler(QueryModel.IsNotEmpty.class, (ctx, criterion) -> {
            ctx.query().append(IS_DEFINED).append(OPEN_BRACKET);
            appendPropertyRef(ctx.query(), ctx.getRequiredProperty(criterion));
            ctx.query().append(CLOSE_BRACKET).append(SPACE).append(AND).append(SPACE);
            ctx.query().append(NOT).append(SPACE).append(IS_NULL).append(OPEN_BRACKET);
            appendPropertyRef(ctx.query(), ctx.getRequiredProperty(criterion));
            ctx.query().append(CLOSE_BRACKET).append(SPACE).append(AND).append(SPACE);
            appendPropertyRef(ctx.query(), ctx.getRequiredProperty(criterion));
            ctx.query().append(NOT_EQUALS).append("''");
        });
        addCriterionHandler(QueryModel.ArrayContains.class, (ctx, criterion) -> {
            QueryPropertyPath propertyPath = ctx.getRequiredProperty(criterion.getProperty(), QueryModel.ArrayContains.class);
            StringBuilder whereClause = ctx.query();
            whereClause.append(ARRAY_CONTAINS).append(OPEN_BRACKET);
            appendPropertyRef(whereClause, propertyPath);
            whereClause.append(COMMA);
            Object value = criterion.getValue();
            if (value instanceof BindingParameter) {
                ctx.pushParameter((BindingParameter) value, newBindingContext(propertyPath.getPropertyPath()));
            } else {
                asLiterals(ctx.query(), value);
            }
            whereClause.append(COMMA).append("true").append(CLOSE_BRACKET);
        });
    }
}
