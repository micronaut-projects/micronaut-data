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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.document.mongo.MongoAnnotations;
import io.micronaut.data.exceptions.MappingException;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentEntityUtils;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.IExpression;
import io.micronaut.data.model.jpa.criteria.IPredicate;
import io.micronaut.data.model.jpa.criteria.ISelection;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.PersistentEntitySubquery;
import io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils;
import io.micronaut.data.model.jpa.criteria.impl.SelectionVisitor;
import io.micronaut.data.model.jpa.criteria.impl.DefaultOrder;
import io.micronaut.data.model.jpa.criteria.impl.expression.BinaryExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.BinaryExpressionType;
import io.micronaut.data.model.jpa.criteria.impl.expression.CastExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.CurrentTemporalExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.FunctionExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.IdExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.LiteralExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.UnaryExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.UnaryExpressionType;
import io.micronaut.data.model.jpa.criteria.impl.predicate.BetweenPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.ConjunctionPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.DisjunctionPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.ExistsSubqueryPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.InPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.LikePredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.NearPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.NegatedPredicate;
import io.micronaut.data.model.jpa.criteria.impl.selection.AliasedSelection;
import io.micronaut.data.model.jpa.criteria.impl.selection.CompoundSelection;
import io.micronaut.data.model.naming.NamingStrategy;
import io.micronaut.data.model.query.BindingParameter;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryParameterBinding;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.model.query.impl.AdvancedPredicateVisitor;
import io.micronaut.data.annotation.search.SearchField;
import io.micronaut.data.annotation.search.SearchWeight;
import io.micronaut.data.model.runtime.convert.MongoTextSearchConverter;
import io.micronaut.data.model.search.MatchMode;
import io.micronaut.data.model.search.SearchCapability;
import io.micronaut.data.model.search.TextSearchSupport;
import io.micronaut.data.model.search.UnsupportedSearchFeatureException;
import io.micronaut.data.model.query.builder.sql.VectorScoringDialectSupport;
import io.micronaut.serde.config.annotation.SerdeConfig;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Nulls;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Selection;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Pattern;

import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.requireProperty;
import static java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME;
import static java.util.Arrays.asList;

/**
 * The Mongo query builder.
 *
 * @author Denis Stepanov
 * @since 3.3
 */
@TypeHint(MongoQueryBuilder.class)
@Internal
public final class MongoQueryBuilder implements QueryBuilder {

    /**
     * An object with this property is replaced with an actual query parameter at the runtime.
     */
    public static final String QUERY_PARAMETER_PLACEHOLDER = "$mn_qp";
    public static final String NEGATE = "$mn_negate"; // -vale
    public static final String RECIPROCATE = "$mn_reciprocate"; // 1/value
    public static final String MONGO_DATE_IDENTIFIER = "$date";
    public static final String MONGO_ID_FIELD = "_id";
    /**
     * An object with this property next to the {@link #QUERY_PARAMETER_PLACEHOLDER} is replaced with a
     * MongoDB Search operator rendered from the bound search string at runtime; the value is the
     * {@link MatchMode} name.
     */
    public static final String SEARCH_OPERATOR_PLACEHOLDER = "$mn_search";
    /**
     * The field paths a {@link #SEARCH_OPERATOR_PLACEHOLDER} searches.
     */
    public static final String SEARCH_PATHS = "$mn_paths";
    /**
     * The boosts of the {@link #SEARCH_PATHS}, by path, when they differ from 1.
     */
    public static final String SEARCH_BOOSTS = "$mn_boosts";
    private static final String GEOMETRY_OPERATOR = "$geometry";
    private static final String META = "$meta";
    private static final Map<String, Object> TEXT_SCORE = Map.of(META, "textScore");
    private static final Map<String, Object> SEARCH_SCORE = Map.of(META, "searchScore");
    private static final Map<String, Object> SEARCH_HIGHLIGHTS = Map.of(META, "searchHighlights");
    private static final String SEARCH_STAGE = "$search";
    private static final String VECTOR_SEARCH_STAGE = "$vectorSearch";
    private static final String DEFAULT_SEARCH_INDEX = "default";
    private static final String DEFAULT_VECTOR_INDEX = "vector";
    private static final int DEFAULT_VECTOR_LIMIT = 100;
    private static final int MAX_VECTOR_CANDIDATES = 10000;
    private static final String ADD_FIELDS = "$addFields";
    private static final String NULL_RANK_FIELD_PREFIX = "__micronaut_nulls_";
    private static final String SORT_EXPRESSION_FIELD_PREFIX = "__micronaut_sort_";
    private static final String STR_LEN_CP = "$strLenCP";
    private static final String TO_LOWER = "$toLower";
    private static final String REGEX = "$regex";
    private static final String NOT = "$not";
    private static final String OPTIONS = "$options";

    @Nullable
    @Override
    public QueryResult buildInsert(AnnotationMetadata repositoryMetadata, InsertQueryDefinition insertQueryDefinition) {
        return null;
    }

    @Override
    public QueryResult buildSelect(AnnotationMetadata annotationMetadata, SelectQueryDefinition selectQueryDefinition) {
        ArgumentUtils.requireNonNull("annotationMetadata", annotationMetadata);
        ArgumentUtils.requireNonNull("selectQueryDefinition", selectQueryDefinition);

        QueryState queryState = new QueryState(selectQueryDefinition, true);

        Map<String, Object> predicateObj = new LinkedHashMap<>();
        Map<String, Object> group = new LinkedHashMap<>();
        Map<String, Object> projectionObj = new LinkedHashMap<>();
        Map<String, Object> countObj = new LinkedHashMap<>();
        Map<String, Object> addFieldsObj = new LinkedHashMap<>();

        addLookups(selectQueryDefinition.getJoinPaths(), queryState);
        List<Map<String, Object>> pipeline = queryState.rootLookups.pipeline;
        buildProjection(selectQueryDefinition.selection(), queryState, group, projectionObj, countObj, addFieldsObj);
        Predicate predicate = selectQueryDefinition.predicate();
        if (predicate != null) {
            predicateObj = buildWhereClause(predicate, queryState);
        }

        appendVectorSearchStage(queryState, selectQueryDefinition, pipeline);
        appendSearchStages(queryState, pipeline);
        if (!predicateObj.isEmpty()) {
            pipeline.add(Map.of("$match", predicateObj));
        }
        if (!addFieldsObj.isEmpty()) {
            pipeline.add(Map.of(ADD_FIELDS, addFieldsObj));
        }
        appendHighlightFields(queryState, pipeline);
        if (!group.isEmpty()) {
            group.put(MONGO_ID_FIELD, null);
            pipeline.add(Map.of("$group", group));
        }
        if (!countObj.isEmpty()) {
            pipeline.add(countObj);
        }
        // Order should be before project as it can remove fields that are needed for ordering
        appendSort(annotationMetadata, selectQueryDefinition.order(), queryState, pipeline);
        appendProjection(annotationMetadata, projectionObj, pipeline);
        appendSkipAndLimit(selectQueryDefinition, pipeline);
        return QueryResult.of(render(pipeline, predicateObj), queryState.getParameterBindings());
    }

    /** Adds the {@code $vectorSearch} stage of a vector predicate or ordering, sized for the query's window, with its distance as a field. */
    private void appendVectorSearchStage(QueryState queryState, SelectQueryDefinition selectQueryDefinition, List<Map<String, Object>> pipeline) {
        if (queryState.vectorSearch == null) {
            return;
        }
        if (queryState.searchOperator != null) {
            throw new UnsupportedOperationException("MongoDB $vectorSearch cannot be combined with $search in one aggregation; a hybrid search runs the two queries and fuses their rankings.");
        }
        if (!pipeline.isEmpty()) {
            throw new UnsupportedOperationException("MongoDB $vectorSearch must be the first stage of the aggregation, so a vector predicate cannot be combined with joins.");
        }
        int limit = selectQueryDefinition.limit() > 0 ? selectQueryDefinition.limit() + Math.max(selectQueryDefinition.offset(), 0) : DEFAULT_VECTOR_LIMIT;
        Map<String, Object> vectorSearch = new LinkedHashMap<>(queryState.vectorSearch);
        vectorSearch.put("numCandidates", Math.min(MAX_VECTOR_CANDIDATES, Math.max(limit * 10, DEFAULT_VECTOR_LIMIT)));
        vectorSearch.put("limit", limit);
        pipeline.add(Map.of(VECTOR_SEARCH_STAGE, vectorSearch));
        if (!queryState.vectorScoreFields.isEmpty()) {
            Map<String, Object> vectorMeta = new LinkedHashMap<>();
            for (String scoreField : queryState.vectorScoreFields) {
                vectorMeta.put(scoreField, MongoSearchStages.vectorDistance(queryState.getEntity()));
            }
            // The distance is only available as metadata of the $vectorSearch stage
            pipeline.add(Map.of(ADD_FIELDS, vectorMeta));
        }
    }

    /** Adds the {@code $search} stage of a search predicate, with its score and highlight metadata as fields. */
    private void appendSearchStages(QueryState queryState, List<Map<String, Object>> pipeline) {
        if (queryState.searchOperator == null) {
            return;
        }
        if (!pipeline.isEmpty()) {
            throw new UnsupportedOperationException("MongoDB $search must be the first stage of the aggregation, so a search predicate cannot be combined with joins.");
        }
        pipeline.add(Map.of(SEARCH_STAGE, buildSearchStage(queryState)));
        Map<String, Object> searchMeta = new LinkedHashMap<>();
        for (String scoreField : queryState.searchScoreFields) {
            searchMeta.put(scoreField, SEARCH_SCORE);
        }
        if (!queryState.highlights.isEmpty()) {
            searchMeta.put(MongoSearchStages.HIGHLIGHTS_FIELD, SEARCH_HIGHLIGHTS);
        }
        if (!searchMeta.isEmpty()) {
            // The score and the highlights are only available as metadata of the $search stage
            pipeline.add(Map.of(ADD_FIELDS, searchMeta));
        }
    }

    /** Renders the highlighted fragments of each highlighted path and drops the raw metadata. */
    private static void appendHighlightFields(QueryState queryState, List<Map<String, Object>> pipeline) {
        if (queryState.highlights.isEmpty()) {
            return;
        }
        Map<String, Object> highlightFields = new LinkedHashMap<>();
        for (Map.Entry<String, String> highlight : queryState.highlights.entrySet()) {
            highlightFields.put(highlight.getKey(), MongoSearchStages.highlightExpression(highlight.getValue()));
        }
        pipeline.add(Map.of(ADD_FIELDS, highlightFields));
        pipeline.add(Map.of("$unset", List.of(MongoSearchStages.HIGHLIGHTS_FIELD)));
    }

    /**
     * Adds the {@code $sort} stage, with the computed fields and null ranks it sorts on defined before it
     * and removed after it, or the repository's custom sort.
     */
    private void appendSort(AnnotationMetadata annotationMetadata, List<Order> orders, QueryState queryState, List<Map<String, Object>> pipeline) {
        if (orders.isEmpty()) {
            String customSort = annotationMetadata.stringValue(MongoAnnotations.SORT).orElse(null);
            if (customSort != null) {
                pipeline.add(Map.of("$sort", new MongoPipelineJson.RawJsonValue(customSort)));
            }
            return;
        }
        SortStages stages = new SortStages();
        for (Order order : orders) {
            appendOrder(order, stages, queryState);
        }
        if (!stages.computedFields.isEmpty() && !stages.nullRanksOverComputedFields) {
            // Nothing ranks a computed field, so one stage can define them all
            stages.computedFields.putAll(stages.nullRankFields);
            stages.nullRankFields.clear();
        }
        if (!stages.computedFields.isEmpty()) {
            pipeline.add(Map.of(ADD_FIELDS, stages.computedFields));
        }
        if (!stages.nullRankFields.isEmpty()) {
            // A second stage, as $addFields cannot reference a field defined by the same stage
            pipeline.add(Map.of(ADD_FIELDS, stages.nullRankFields));
        }
        pipeline.add(Map.of("$sort", stages.sortObj));
        List<String> temporaryFields = new ArrayList<>(stages.computedFields.keySet());
        temporaryFields.addAll(stages.nullRankFields.keySet());
        if (!temporaryFields.isEmpty()) {
            pipeline.add(Map.of("$unset", temporaryFields));
        }
    }

    /** Adds one order to the sort: a property, or a computed field, with its null rank when one is asked for. */
    private void appendOrder(Order order, SortStages stages, QueryState queryState) {
        Expression<?> orderExpression = order.getExpression();
        String fieldName;
        if (orderExpression instanceof io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> persistentPropertyPath) {
            fieldName = asSortFieldName(persistentPropertyPath);
        } else {
            // Ordering by a computed expression, which MongoDB can only do against a field
            fieldName = SORT_EXPRESSION_FIELD_PREFIX + stages.computedFields.size();
            stages.computedFields.put(fieldName, asAggregationExpression(orderExpression, queryState));
        }
        Nulls nullPrecedence = order instanceof DefaultOrder<?> defaultOrder ? defaultOrder.getNullPrecedence() : Nulls.NONE;
        if (nullPrecedence != Nulls.NONE) {
            String rankField = NULL_RANK_FIELD_PREFIX + stages.nullRankFields.size();
            stages.nullRankFields.put(rankField, nullRankExpression(fieldName, nullPrecedence));
            stages.sortObj.put(rankField, 1);
            stages.nullRanksOverComputedFields |= stages.computedFields.containsKey(fieldName);
        }
        stages.sortObj.put(fieldName, order.isAscending() ? 1 : -1);
    }

    /** Adds the {@code $project} stage of the projection, or the repository's custom projection. */
    private static void appendProjection(AnnotationMetadata annotationMetadata, Map<String, Object> projectionObj, List<Map<String, Object>> pipeline) {
        if (!projectionObj.isEmpty()) {
            pipeline.add(Map.of("$project", projectionObj));
        } else {
            String customProjection = annotationMetadata.stringValue(MongoAnnotations.PROJECTION).orElse(null);
            if (customProjection != null) {
                pipeline.add(Map.of("$project", new MongoPipelineJson.RawJsonValue(customProjection)));
            }
        }
    }

    private static void appendSkipAndLimit(SelectQueryDefinition selectQueryDefinition, List<Map<String, Object>> pipeline) {
        if (selectQueryDefinition.offset() > 0) {
            pipeline.add(Map.of("$skip", selectQueryDefinition.offset()));
        }
        if (selectQueryDefinition.limit() != -1) {
            pipeline.add(Map.of("$limit", selectQueryDefinition.limit()));
        }
    }

    /** @return The pipeline as JSON, or the bare filter when the pipeline is one {@code $match} stage */
    private String render(List<Map<String, Object>> pipeline, Map<String, Object> predicateObj) {
        if (pipeline.isEmpty()) {
            return "{}";
        }
        if (isMatchOnlyStage(pipeline)) {
            return MongoPipelineJson.toJsonString(predicateObj);
        }
        return MongoPipelineJson.toJsonString(pipeline);
    }

    /**
     * Renders a criteria expression that is ordered by into the equivalent MongoDB aggregation expression.
     *
     * @param expression The expression
     * @param queryState The query state, used to bind any parameters the expression carries
     * @return The aggregation expression
     */
    private Object asAggregationExpression(Expression<?> expression, QueryState queryState) {
        if (expression instanceof io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> propertyPath) {
            return "$" + asSortFieldName(propertyPath);
        }
        if (expression instanceof LiteralExpression<?> literalExpression) {
            Object value = literalExpression.getValue();
            if (value == null) {
                throw new UnsupportedOperationException("Ordering by a null literal is not supported by Micronaut Data MongoDB.");
            }
            return value;
        }
        if (expression instanceof BinaryExpression<?> binaryExpression) {
            String operator = switch (binaryExpression.getType()) {
                case SUM -> "$add";
                case DIFF -> "$subtract";
                case PROD -> "$multiply";
                case QUOT -> "$divide";
                case CONCAT -> "$concat";
            };
            return Map.of(operator, List.of(
                asAggregationExpression(binaryExpression.getLeft(), queryState),
                asAggregationExpression(binaryExpression.getRight(), queryState)
            ));
        }
        if (expression instanceof UnaryExpression<?> unaryExpression) {
            String operator = switch (unaryExpression.getType()) {
                case LOWER -> TO_LOWER;
                case UPPER -> "$toUpper";
                case LENGTH -> STR_LEN_CP;
                default -> throw new UnsupportedOperationException(
                    "Ordering by expression: " + unaryExpression + " is not supported by Micronaut Data MongoDB.");
            };
            return Map.of(operator, asAggregationExpression(unaryExpression.getExpression(), queryState));
        }
        if (expression instanceof FunctionExpression<?> functionExpression) {
            return asAggregationFunction(functionExpression, queryState);
        }
        if (expression instanceof BindingParameter bindingParameter) {
            return Map.of(QUERY_PARAMETER_PLACEHOLDER, queryState.pushParameter(bindingParameter, newBindingContext(null)));
        }
        throw new UnsupportedOperationException("Ordering by expression: " + expression + " is not supported by Micronaut Data MongoDB.");
    }

    private Object asAggregationFunction(FunctionExpression<?> functionExpression, QueryState queryState) {
        if (TextSearchSupport.SCORE_FUNCTION.equals(functionExpression.getName())) {
            return usesSearchIndex(queryState.getEntity()) ? SEARCH_SCORE : TEXT_SCORE;
        }
        if (VectorScoringDialectSupport.SCORE_FUNCTION.equals(functionExpression.getName())) {
            registerVectorSearch(queryState, functionExpression);
            return MongoSearchStages.vectorDistance(queryState.getEntity());
        }
        List<Expression<?>> arguments = functionExpression.getExpressions();
        String name = functionExpression.getName().toUpperCase(Locale.ROOT);
        if (arguments.size() == 2 && ("LEFT".equals(name) || "RIGHT".equals(name))) {
            Object value = asAggregationExpression(arguments.get(0), queryState);
            Object length = asAggregationExpression(arguments.get(1), queryState);
            Object start = "LEFT".equals(name)
                ? 0
                : Map.of("$subtract", List.of(Map.of(STR_LEN_CP, value), length));
            return Map.of("$substrCP", List.of(value, start, length));
        }
        if (arguments.size() == 1) {
            String operator = switch (name) {
                case "LOWER" -> TO_LOWER;
                case "UPPER" -> "$toUpper";
                case "LENGTH" -> STR_LEN_CP;
                default -> null;
            };
            if (operator != null) {
                return Map.of(operator, asAggregationExpression(arguments.get(0), queryState));
            }
        }
        throw new UnsupportedOperationException("Function: " + functionExpression.getName() + " is not supported by Micronaut Data MongoDB.");
    }

    /**
     * Resolves the field a property path sorts on, mapping an identity property onto MongoDB's {@code _id}.
     * A path into an association keeps its prefix, so the identity of an associated entity maps onto
     * {@code <association>._id} rather than the root's {@code _id}.
     *
     * @param propertyPath The property path
     * @return The field name to sort on
     */
    private String asSortFieldName(io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> criteriaPath) {
        PersistentPropertyPath propertyPath = criteriaPath.getPropertyPath();
        StringBuilder fieldName = new StringBuilder();
        for (Association association : propertyPath.getAssociations()) {
            fieldName.append(getPropertyPersistName(association)).append('.');
        }
        return fieldName.append(getPropertyPersistName(propertyPath.getProperty())).toString();
    }

    /**
     * Builds the expression ranking documents by whether the sorted field is null or missing, so that an
     * ascending sort on the rank places nulls where the caller asked for them. MongoDB's own ordering
     * always puts null and missing values first in ascending order and last in descending order.
     *
     * @param fieldName      The field being sorted on
     * @param nullPrecedence Where null values belong
     * @return The rank expression
     */
    private static Map<String, Object> nullRankExpression(String fieldName, Nulls nullPrecedence) {
        int nullRank = nullPrecedence == Nulls.FIRST ? 0 : 1;
        return Map.of("$cond", List.of(
            Map.of("$in", List.of(Map.of("$type", "$" + fieldName), List.of("missing", "null"))),
            nullRank,
            1 - nullRank
        ));
    }

    private void addLookups(Collection<JoinPath> joins, QueryState queryState) {
        if (joins.isEmpty()) {
            return;
        }
        List<String> joined = joins.stream().map(JoinPath::getPath)
            .sorted((o1, o2) -> Comparator.comparingInt(String::length).thenComparing(String::compareTo).compare(o1, o2))
            .toList();
        for (String join : joined) {
            StringJoiner rootPath = new StringJoiner(".");
            StringJoiner currentEntityPath = new StringJoiner(".");
            LookupsStage currentLookup = queryState.rootLookups;
            for (String path : StringUtils.splitOmitEmptyStrings(join, '.')) {
                rootPath.add(path);
                currentEntityPath.add(path);
                String thisPath = currentEntityPath.toString();
                LookupsStage subLookup = currentLookup.subLookups.get(thisPath);
                if (subLookup != null) {
                    // The path from the current entity can span several segments, for example through an embedded property
                    currentLookup = subLookup;
                    currentEntityPath = new StringJoiner(".");
                    continue;
                }

                PersistentPropertyPath propertyPath = currentLookup.persistentEntity.getPropertyPath(thisPath);
                Objects.requireNonNull(propertyPath, "Cannot find property path: " + thisPath);
                PersistentProperty property = propertyPath.getProperty();
                if (!(property instanceof Association association)) {
                    continue;
                }
                if (association.getKind() == Relation.Kind.EMBEDDED) {
                    continue;
                }
                LookupsStage lookupStage = new LookupsStage(association.getAssociatedEntity());
                List<Map<String, Object>> pipeline = currentLookup.pipeline;
                Optional<Association> inverseSide = association.getInverseSide().map(Function.identity());
                PersistentEntity persistentEntity = association.getOwner();

                String joinedCollectionName = association.getAssociatedEntity().getPersistedName();
                String ownerCollectionName = persistentEntity.getPersistedName();
                if (association.getKind() == Relation.Kind.MANY_TO_MANY || association.isForeignKey() && !inverseSide.isPresent()) {
                    PersistentEntity associatedEntity = association.getAssociatedEntity();
                    PersistentEntity associationOwner = association.getOwner();
                    // JOIN TABLE
                    if (!associatedEntity.hasIdentity()) {
                        throw new IllegalArgumentException("Associated entity [" + associatedEntity.getName() + "] defines no ID. Cannot join.");
                    }
                    if (!associationOwner.hasIdentity()) {
                        throw new MappingException("Cannot join on entity [" + associationOwner.getName() + "] that has no declared ID");
                    }
                    Association owningAssociation = inverseSide.orElse(association);
                    boolean isAssociationOwner = association.getInverseSide().isEmpty();
                    NamingStrategy namingStrategy = associationOwner.getNamingStrategy();
                    AnnotationMetadata annotationMetadata = owningAssociation.getAnnotationMetadata();

                    List<String> ownerJoinFields = resolveJoinTableAssociatedFields(annotationMetadata, isAssociationOwner, associationOwner, namingStrategy);
                    List<String> ownerJoinCollectionFields = resolveJoinTableJoinFields(annotationMetadata, isAssociationOwner, associationOwner, namingStrategy);
                    List<String> associationJoinFields = resolveJoinTableAssociatedFields(annotationMetadata, !isAssociationOwner, associatedEntity, namingStrategy);
                    List<String> associationJoinCollectionFields = resolveJoinTableJoinFields(annotationMetadata, !isAssociationOwner, associatedEntity, namingStrategy);

                    String joinCollectionName = namingStrategy.mappedName(owningAssociation);

//                        String joinTableName = annotationMetadata
//                                .stringValue(ANN_JOIN_TABLE, "name")
//                                .orElseGet(() -> namingStrategy.mappedName(association));

                    List<Map<String, Object>> joinCollectionLookupPipeline = new ArrayList<>();
                    pipeline.add(lookup(joinCollectionName, MONGO_ID_FIELD, ownerCollectionName, joinCollectionLookupPipeline, thisPath));
                    joinCollectionLookupPipeline.add(
                        lookup(
                            joinedCollectionName,
                            joinedCollectionName,
                            MONGO_ID_FIELD,
                            lookupStage.pipeline,
                            joinedCollectionName)
                    );
                    joinCollectionLookupPipeline.add(unwind("$" + joinedCollectionName, true));
                    joinCollectionLookupPipeline.add(
                        Map.of("$replaceRoot", Map.of("newRoot", "$" + joinedCollectionName))
                    );
                } else {
                    String currentPath = asPath(propertyPath.getAssociations(), propertyPath.getProperty());
                    if (association.isForeignKey()) {
                        String mappedBy = association.getAnnotationMetadata().stringValue(Relation.class, "mappedBy")
                            .orElseThrow(IllegalStateException::new);
                        PersistentPropertyPath mappedByPath = association.getAssociatedEntity().getPropertyPath(mappedBy);
                        if (mappedByPath == null) {
                            throw new IllegalStateException("Cannot find mapped path: " + mappedBy);
                        }
                        if (!(mappedByPath.getProperty() instanceof Association associationProperty)) {
                            throw new IllegalStateException("Expected association as a mapped path: " + mappedBy);
                        }

                        var localMatchFields = new ArrayList<String>();
                        var foreignMatchFields = new ArrayList<String>();
                        PersistentEntityUtils.traversePersistentProperties(currentLookup.persistentEntity.getIdentity(), (associations, p) -> {
                            localMatchFields.add(asPath(associations, p));
                        });

                        var mappedAssociations = new ArrayList<>(mappedByPath.getAssociations());
                        mappedAssociations.add(associationProperty);

                        PersistentEntityUtils.traversePersistentProperties(mappedAssociations, currentLookup.persistentEntity.getIdentity(), (associations, p) -> {
                            String fieldPath = asPath(associations, p);
                            foreignMatchFields.add(fieldPath);
                        });

                        pipeline.add(lookup(
                            joinedCollectionName,
                            localMatchFields,
                            foreignMatchFields,
                            lookupStage.pipeline,
                            currentPath)
                        );
                    } else {
                        var mappedAssociations = new ArrayList<>(propertyPath.getAssociations());
                        mappedAssociations.add((Association) propertyPath.getProperty());

                        var localMatchFields = new ArrayList<String>();
                        var foreignMatchFields = new ArrayList<String>();
                        if (!lookupStage.persistentEntity.hasIdentity()) {
                            throw new IllegalStateException("Null identity of persistent entity: " + lookupStage.persistentEntity);
                        }
                        PersistentProperty identity = lookupStage.persistentEntity.getIdentity();
                        PersistentEntityUtils.traversePersistentProperties(mappedAssociations, identity, (associations, p) -> {
                            localMatchFields.add(asPath(associations, p));
                        });
                        PersistentEntityUtils.traversePersistentProperties(identity, (associations, p) -> {
                            foreignMatchFields.add(asPath(associations, p));
                        });

                        pipeline.add(lookup(
                            joinedCollectionName,
                            localMatchFields,
                            foreignMatchFields,
                            lookupStage.pipeline,
                            currentPath)
                        );
                    }
                    if (association.getKind().isSingleEnded()) {
                        pipeline.add(unwind("$" + currentPath, true));
                    }
                }
                currentLookup.subLookups.put(currentEntityPath.toString(), lookupStage);
            }
            queryState.joinPaths.add(join);
        }
    }

    @NonNull
    private List<String> resolveJoinTableJoinFields(AnnotationMetadata annotationMetadata, boolean associationOwner, PersistentEntity entity, NamingStrategy namingStrategy) {
        List<String> joinColumns = getJoinedFields(annotationMetadata, associationOwner, "name");
        if (!joinColumns.isEmpty()) {
            return joinColumns;
        }
        var fields = new ArrayList<String>();
        PersistentEntityUtils.traversePersistentProperties(entity.getIdentity(), (associations, property) -> fields.add(asPath(associations, property)));
        return fields;
    }

    @NonNull
    private List<String> resolveJoinTableAssociatedFields(AnnotationMetadata annotationMetadata, boolean associationOwner, PersistentEntity entity, NamingStrategy namingStrategy) {
        List<String> joinColumns = getJoinedFields(annotationMetadata, associationOwner, "referencedColumnName");
        if (!joinColumns.isEmpty()) {
            return joinColumns;
        }
        if (!entity.hasIdentity()) {
            throw new MappingException("Cannot have a foreign key association without an ID on entity: " + entity.getName());
        }
        PersistentProperty identity = entity.getIdentity();
        var fields = new ArrayList<String>();
        PersistentEntityUtils.traversePersistentProperties(identity, (associations, property) -> {
            fields.add(asPath(associations, property));
        });
        return fields;
    }

    @NonNull
    private List<String> getJoinedFields(AnnotationMetadata annotationMetadata, boolean associationOwner, String columnType) {
        // TODO: support @JoinTable style annotation
        return Collections.emptyList();
    }

    private String asPath(List<Association> associations, PersistentProperty property) {
        if (associations.isEmpty()) {
            return getPropertyPersistName(property);
        }
        var joiner = new StringJoiner(".");
        for (Association association : associations) {
            joiner.add(getPropertyPersistName(association));
        }
        joiner.add(getPropertyPersistName(property));
        return joiner.toString();
    }

    private Map<String, Object> lookup(String from, String localField, String foreignField, List<Map<String, Object>> pipeline, String as) {
        Map<String, Object> lookup = new LinkedHashMap<>();
        lookup.put("from", from);
        lookup.put("localField", localField);
        lookup.put("foreignField", foreignField);
        lookup.put("pipeline", pipeline);
        lookup.put("as", as);
        return Map.of("$lookup", lookup);
    }

    private Map<String, Object> lookup(String from,
                                       List<String> localFields,
                                       List<String> foreignFields,
                                       List<Map<String, Object>> pipeline,
                                       String as) {
        if (localFields.size() != foreignFields.size()) {
            throw new IllegalStateException("Un-matching join columns size: " + localFields.size() + " != " + foreignFields.size() + " " + localFields + ", " + foreignFields);
        }
        if (localFields.size() == 1) {
            return lookup(from, localFields.iterator().next(), foreignFields.iterator().next(), pipeline, as);
        }
        List<Map<String, Object>> matches = new ArrayList<>(localFields.size());
        Map<String, Object> let = new LinkedHashMap<>();
        int i = 1;
        Iterator<String> foreignIt = foreignFields.iterator();
        for (String localField : localFields) {
            String var = "v" + i++;
            let.put(var, "$" + localField);
            matches.add(Map.of("$eq", Arrays.asList("$$" + var, "$" + foreignIt.next())));
        }

        Map<String, Object> match;
        if (matches.size() > 1) {
            match = Map.of("$match", Map.of("$expr", Map.of("$and", matches)));
        } else {
            match = Map.of("$match", Map.of("$expr", matches.iterator().next()));
        }

        return lookup(from, let, match, pipeline, as);
    }

    private Map<String, Object> lookup(String from,
                                       Map<String, Object> let,
                                       Map<String, Object> match,
                                       List<Map<String, Object>> pipeline,
                                       String as) {

        pipeline.add(match);
        Map<String, Object> lookup = new LinkedHashMap<>();
        lookup.put("from", from);
        lookup.put("let", let);
        lookup.put("pipeline", pipeline);
        lookup.put("as", as);
        return Map.of("$lookup", lookup);
    }

    private Map<String, Object> unwind(String path, boolean preserveNullAndEmptyArrays) {
        Map<String, Object> unwind = new LinkedHashMap<>();
        unwind.put("path", path);
        unwind.put("preserveNullAndEmptyArrays", preserveNullAndEmptyArrays);
        return Map.of("$unwind", unwind);
    }

    private boolean isMatchOnlyStage(List<Map<String, Object>> pipeline) {
        return pipeline.size() == 1 && pipeline.iterator().next().containsKey("$match");
    }

    private Map<String, Object> buildWhereClause(Predicate predicate, QueryState queryState) {
        if (predicate == null) {
            return Map.of();
        }
        Map<String, Object> query = new LinkedHashMap<>();
        if (predicate instanceof IPredicate predicateVisitable) {
            predicateVisitable.visitPredicate(new MongoPredicateVisitor(queryState, query));
        } else {
            throw new IllegalStateException("Unsupported predicate type: " + predicate.getClass().getName());
        }
        if (queryState.searchOperator != null || queryState.vectorSearch != null) {
            MongoSearchStages.pruneEmptyConjunction(query);
        }
        return query;
    }

    private void buildProjection(Selection<?> selection,
                                 QueryState queryState,
                                 Map<String, Object> groupObj,
                                 Map<String, Object> projectionObj,
                                 Map<String, Object> countObj,
                                 Map<String, Object> addFieldsObj) {
        if (selection == null) {
            return;
        }
        if (selection instanceof ISelection<?> selectionVisitable) {
            selectionVisitable.visitSelection(new MongoSelectionVisitor(queryState, projectionObj, groupObj, countObj, addFieldsObj));
        } else {
            throw new IllegalStateException("Unsupported selection type: " + selection.getClass().getName());
        }
    }

    /**
     * @param entity The entity
     * @return Whether the entity is searched with MongoDB Search rather than {@code $text}
     */
    private static boolean usesSearchIndex(PersistentEntity entity) {
        return entity.getAnnotationMetadata().hasAnnotation(MongoAnnotations.SEARCH_INDEX);
    }

    /**
     * Records the {@code $vectorSearch} stage of a {@code mn_vector_score(field, vector)} function; the stage
     * gets its limit when the pipeline is assembled and the vector is encoded as an array at runtime.
     */
    private void registerVectorSearch(QueryState queryState, FunctionExpression<?> function) {
        if (!usesSearchIndex(queryState.getEntity())) {
            throw new UnsupportedOperationException("Vector search needs a @MongoSearchIndex on the entity; MongoDB $text has no vector search.");
        }
        if (queryState.vectorSearch != null) {
            return;
        }
        PersistentPropertyPath propertyPath = requireProperty(function.getExpressions().get(0)).getPropertyPath();
        Expression<?> vector = function.getExpressions().get(1);
        if (!(vector instanceof BindingParameter bindingParameter)) {
            throw new IllegalStateException("The query vector must be a parameter, got: " + vector);
        }
        int index = queryState.pushParameter(bindingParameter, newBindingContext(null));
        Map<String, Object> stage = new LinkedHashMap<>();
        stage.put("index", searchIndexMember(queryState.getEntity(), "vectorIndex", DEFAULT_VECTOR_INDEX));
        stage.put("path", getPropertyPersistName(propertyPath));
        stage.put("queryVector", Map.of(QUERY_PARAMETER_PLACEHOLDER, index));
        queryState.vectorSearch = stage;
    }

    private Map<String, Object> buildSearchStage(QueryState queryState) {
        String index = searchIndexMember(queryState.getEntity(), "name", DEFAULT_SEARCH_INDEX);
        return MongoSearchStages.searchStage(index, Objects.requireNonNull(queryState.searchOperator), queryState.highlights.values());
    }

    private static String searchIndexMember(PersistentEntity entity, String member, String defaultValue) {
        return entity.getAnnotationMetadata().stringValue(MongoAnnotations.SEARCH_INDEX, member).filter(v -> !v.isEmpty()).orElse(defaultValue);
    }

    @NonNull
    private PersistentPropertyPath findProperty(QueryState queryState, String name) {
        return findPropertyInternal(queryState, queryState.getEntity(), name);
    }

    private PersistentPropertyPath findPropertyInternal(QueryState queryState, PersistentEntity entity, String name) {
        PersistentPropertyPath propertyPath = entity.getPropertyPath(name);
        if (propertyPath != null) {
            if (propertyPath.getAssociations().isEmpty()) {
                return propertyPath;
            }
            Association joinAssociation = null;
            StringJoiner joinPathJoiner = new StringJoiner(".");
            for (Association association : propertyPath.getAssociations()) {
                joinPathJoiner.add(association.getName());
                if (association.isEmbedded()) {
                    continue;
                }
                if (joinAssociation == null) {
                    joinAssociation = association;
                    continue;
                }
                if (joinAssociation.getAssociatedEntity().hasIdentity() && association != joinAssociation.getAssociatedEntity().getIdentity()) {
                    if (!queryState.isAllowJoins()) {
                        throw new IllegalArgumentException("Joins cannot be used in a DELETE or UPDATE operation");
                    }
                    String joinStringPath = joinPathJoiner.toString();
                    if (!queryState.isJoined(joinStringPath)) {
                        throw new IllegalArgumentException("Property is not joined at path: " + joinStringPath);
                    }
                    // Continue to look for a joined property
                    joinAssociation = association;
                } else {
                    // We don't need to join to access the id of the relation
                    joinAssociation = null;
                }
            }
            PersistentProperty property = propertyPath.getProperty();
            if (joinAssociation != null) {
                if (joinAssociation.getAssociatedEntity().hasIdentity() && property != joinAssociation.getAssociatedEntity().getIdentity()) {
                    String joinStringPath = joinPathJoiner.toString();
                    if (!queryState.isJoined(joinStringPath)) {
                        throw new IllegalArgumentException("Property is not joined at path: " + joinStringPath);
                    }
                }
                // We don't need to join to access the id of the relation
            }
        } else if (TypeRole.ID.equals(name) && entity.hasIdentity()) {
            // special case handling for ID
            return PersistentPropertyPath.of(Collections.emptyList(), entity.getIdentity(), entity.getIdentity().getName());
        }
        if (propertyPath == null) {
            throw new IllegalArgumentException("Cannot order on non-existent property path: " + name);
        }
        return propertyPath;
    }

    @Override
    public QueryResult buildUpdate(AnnotationMetadata annotationMetadata, UpdateQueryDefinition updateQueryDefinition) {

        QueryState queryState = new QueryState(updateQueryDefinition, true);

        Predicate predicate = updateQueryDefinition.predicate();

        String predicateQuery;
        if (predicate != null) {
            predicateQuery = MongoPipelineJson.toJsonString(
                buildWhereClause(predicate, queryState)
            );
        } else {
            predicateQuery = "";
        }

        Map<String, Object> propertiesToUpdate = updateQueryDefinition.propertiesToUpdate();
        Map<String, Object> sets = CollectionUtils.newLinkedHashMap(propertiesToUpdate.size());
        Map<String, Object> incs = CollectionUtils.newLinkedHashMap(propertiesToUpdate.size());
        Map<String, Object> muls = CollectionUtils.newLinkedHashMap(propertiesToUpdate.size());
        for (Map.Entry<String, Object> e : propertiesToUpdate.entrySet()) {
            PersistentPropertyPath propertyPath = findProperty(queryState, e.getKey());
            String propertyPersistName = getPropertyPersistName(propertyPath);
            Object value = e.getValue();
            if (value instanceof BindingParameter bindingParameter) {
                int index = queryState.pushParameter(
                    bindingParameter,
                    newBindingContext(propertyPath)
                );
                sets.put(propertyPersistName, Map.of(QUERY_PARAMETER_PLACEHOLDER, index));
            } else if (value instanceof LiteralExpression<?> literalExpression) {
                sets.put(propertyPersistName, literalExpression.getValue());
            } else if (value instanceof BinaryExpression<?> binaryExpression) {
                io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> leftProp = requireProperty(binaryExpression.getLeft());
                if (!leftProp.getProperty().equals(propertyPath.getProperty())) {
                    throw new IllegalStateException("Left property path does not match property path");
                }
                if (binaryExpression.getRight() instanceof BindingParameter bindingParameter) {
                    int index = queryState.pushParameter(
                        bindingParameter,
                        newBindingContext(propertyPath)
                    );
                    switch (binaryExpression.getType()) {
                        case CONCAT -> {
                        }
                        case SUM ->
                            incs.put(propertyPersistName, Map.of(QUERY_PARAMETER_PLACEHOLDER, index));
                        case PROD ->
                            muls.put(propertyPersistName, Map.of(QUERY_PARAMETER_PLACEHOLDER, index));
                        case QUOT ->
                            muls.put(propertyPersistName, Map.of(QUERY_PARAMETER_PLACEHOLDER, index, RECIPROCATE, true));
                        case DIFF ->
                            incs.put(propertyPersistName, Map.of(QUERY_PARAMETER_PLACEHOLDER, index, NEGATE, true));
                        default -> throw new IllegalStateException("Unsupported binary expression type: " + binaryExpression.getType());
                    }
                    continue;
                }
                throw new IllegalArgumentException("Unsupported expression type: " + value.getClass().getName() + " " + value);
            }
        }

        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        if (!sets.isEmpty()) {
            map.put("$set", sets);
        }
        if (!incs.isEmpty()) {
            map.put("$inc", incs);
        }
        if (!muls.isEmpty()) {
            map.put("$mul", muls);
        }
        String update = MongoPipelineJson.toJsonString(map);

        return new QueryResult() {

            @NonNull
            @Override
            public String getQuery() {
                return predicateQuery;
            }

            @Override
            public String getUpdate() {
                return update;
            }

            @Override
            public List<String> getQueryParts() {
                return Collections.emptyList();
            }

            @Override
            public List<QueryParameterBinding> getParameterBindings() {
                return queryState.getParameterBindings();
            }

            @Override
            public Map<String, String> getAdditionalRequiredParameters() {
                return Collections.emptyMap();
            }

        };
    }

    @Override
    public QueryResult buildDelete(AnnotationMetadata annotationMetadata, DeleteQueryDefinition queryDefinition) {
        ArgumentUtils.requireNonNull("annotationMetadata", annotationMetadata);
        ArgumentUtils.requireNonNull("query", queryDefinition);

        QueryState queryState = new QueryState(queryDefinition, true);

        Predicate predicate = queryDefinition.predicate();

        String predicateQuery = "";
        if (predicate != null) {
            predicateQuery = MongoPipelineJson.toJsonString(
                buildWhereClause(predicate, queryState)
            );
        }

        return QueryResult.of(
            predicateQuery,
            Collections.emptyList(),
            queryState.getParameterBindings(),
            queryState.getAdditionalRequiredParameters()
        );
    }

    @Override
    public String buildLimitAndOffset(long limit, long offset) {
        throw new UnsupportedOperationException();
    }

    private BindingParameter.BindingContext newBindingContext(@Nullable PersistentPropertyPath ref) {
        return newBindingContext(ref, ref);
    }

    private BindingParameter.BindingContext newBindingContext(@Nullable PersistentPropertyPath in, @Nullable PersistentPropertyPath out) {
        return BindingParameter.BindingContext.create()
            .incomingMethodParameterProperty(in)
            .outgoingQueryParameterProperty(out);
    }

    /**
     * Gets criterion property name. Used as sort of adapter if property in criteria should have different name that the persistent property.
     * Used currently for id property name to be generated as _id when used in criteria.
     *
     * @param propertyPath the propertyPath
     * @return resulting name for the criteria, if identity field is used in criteria then returns _id else original criteria property name
     */
    private String getPropertyPersistName(PersistentPropertyPath propertyPath) {
        PersistentProperty property = propertyPath.getProperty();
        if (property.getOwner().hasIdentity() && property.getOwner().getIdentity() == property) {
            return MONGO_ID_FIELD;
        }
        return property.getAnnotationMetadata()
            .stringValue(SerdeConfig.class, SerdeConfig.PROPERTY)
            .orElseGet(propertyPath::getPath);
    }

    private String getPropertyPersistName(PersistentProperty property) {
        if (property.getOwner().hasIdentity() && property.getOwner().getIdentity() == property) {
            return MONGO_ID_FIELD;
        }
        return property.getAnnotationMetadata()
            .stringValue(SerdeConfig.class, SerdeConfig.PROPERTY)
            .orElseGet(property::getName);
    }

    @Nullable
    private Object asLiteral(@Nullable Object value) {
        if (value instanceof RegexPattern regexPattern) {
            return "'" + Pattern.quote(regexPattern.value) + "'";
        }
        return value;
    }

    /**
     * The fields a {@code $sort} stage needs defined around it.
     */
    private static final class SortStages {
        private final Map<String, Object> sortObj = new LinkedHashMap<>();
        private final Map<String, Object> computedFields = new LinkedHashMap<>();
        private final Map<String, Object> nullRankFields = new LinkedHashMap<>();
        private boolean nullRanksOverComputedFields;
    }

    /**
     * The lookups stage data holder.
     */
    private static final class LookupsStage {

        private final PersistentEntity persistentEntity;
        private final List<Map<String, Object>> pipeline = new ArrayList<>();
        private final Map<String, LookupsStage> subLookups = new HashMap<>();

        private LookupsStage(PersistentEntity persistentEntity) {
            this.persistentEntity = persistentEntity;
        }
    }

    /**
     * The state of the query.
     */
    @Internal
    private static final class QueryState implements PropertyParameterCreator {
        private final Set<String> joinPaths = new TreeSet<>();
        private final AtomicInteger position = new AtomicInteger(0);
        private final Map<String, String> additionalRequiredParameters = new LinkedHashMap<>();
        private final List<QueryParameterBinding> parameterBindings;
        private final boolean allowJoins;
        private final PersistentEntity entity;

        private final LookupsStage rootLookups;
        // The MongoDB Search operator placeholder of the search predicate, when the entity has a search index
        @Nullable
        private Map<String, Object> searchOperator;
        // The fields the searchScore is added as
        private final Set<String> searchScoreFields = new LinkedHashSet<>();
        // The $vectorSearch stage of the vector predicate, without its limit, when the entity has a search index
        @Nullable
        private Map<String, Object> vectorSearch;
        // The fields the vector distance is added as
        private final Set<String> vectorScoreFields = new LinkedHashSet<>();
        // The highlighted field paths by the alias of the projected fragment
        private final Map<String, String> highlights = new LinkedHashMap<>();

        private QueryState(BaseQueryDefinition baseQueryDefinition, boolean allowJoins) {
            this.allowJoins = allowJoins;
            this.entity = baseQueryDefinition.persistentEntity();
            this.parameterBindings = new ArrayList<>(entity.getPersistentPropertyNames().size());
            this.rootLookups = new LookupsStage(entity);
        }

        /**
         * @return The entity
         */
        public PersistentEntity getEntity() {
            return entity;
        }

        /**
         * @return Does the query allow joins
         */
        public boolean isAllowJoins() {
            return allowJoins;
        }

        /**
         * Checks if the path is joined already.
         *
         * @param associationPath The association path.
         * @return true if joined
         */
        public boolean isJoined(String associationPath) {
            for (String joinPath : joinPaths) {
                if (joinPath.startsWith(associationPath)) {
                    return true;
                }
            }
            return joinPaths.contains(associationPath);
        }

        /**
         * The additional required parameters.
         *
         * @return The parameters
         */
        public @NonNull Map<String, String> getAdditionalRequiredParameters() {
            return this.additionalRequiredParameters;
        }

        /**
         * The parameter binding.
         *
         * @return The parameter binding
         */
        public List<QueryParameterBinding> getParameterBindings() {
            return parameterBindings;
        }

        @Override
        public int pushParameter(@NonNull BindingParameter bindingParameter, BindingParameter. @NonNull BindingContext bindingContext) {
            int index = position.getAndIncrement();
            bindingContext = bindingContext.index(index);
            parameterBindings.add(
                bindingParameter.bind(bindingContext)
            );
            return index;
        }
    }

    private interface PropertyParameterCreator {

        int pushParameter(@NonNull BindingParameter bindingParameter,
                          BindingParameter. @NonNull BindingContext bindingContext);

    }

    private record RegexPattern(String value) {
    }

    private class MongoPredicateVisitor implements AdvancedPredicateVisitor<PersistentPropertyPath> {

        private final PersistentEntity persistentEntity;
        private final QueryState queryState;
        private Map<String, Object> query;
        private boolean inDisjunction;

        public MongoPredicateVisitor(QueryState queryState, Map<String, Object> query) {
            this.queryState = queryState;
            this.query = query;
            persistentEntity = queryState.getEntity();
        }

        /** Restricts on a search score: the score is added as a field right after the {@code $search} stage, so the {@code $match} can test it. */
        private boolean appendScoreRestriction(FunctionExpression<?> function, String op, Expression<?> value) {
            if (TextSearchSupport.SCORE_FUNCTION.equals(function.getName())) {
                if (!usesSearchIndex(persistentEntity)) {
                    throw new UnsupportedSearchFeatureException(SearchCapability.MIN_SCORE, "MongoDB $text");
                }
                queryState.searchScoreFields.add(TextSearchSupport.SCORE_ALIAS);
                query.put(TextSearchSupport.SCORE_ALIAS, Map.of(op, scalarValue(value)));
                return true;
            }
            if (VectorScoringDialectSupport.SCORE_FUNCTION.equals(function.getName())) {
                // The maximum distance restricts the distance added right after the $vectorSearch stage
                registerVectorSearch(queryState, function);
                queryState.vectorScoreFields.add(TextSearchSupport.SCORE_ALIAS);
                query.put(TextSearchSupport.SCORE_ALIAS, Map.of(op, scalarValue(value)));
                return true;
            }
            return false;
        }

        private void appendOperatorExpression(Expression<?> leftExpression, String op, Expression<?> value) {
            if (leftExpression instanceof FunctionExpression<?> function && appendScoreRestriction(function, op, value)) {
                return;
            }
            if (leftExpression instanceof LiteralExpression<?> leftLiteral && value instanceof LiteralExpression<?> rightLiteral) {
                // A comparison of two constants, for example the 1 = 2 that an empty disjunction is normalized to
                query.put("$expr", Map.of(op, asList(leftLiteral.getValue(), rightLiteral.getValue())));
                return;
            }
            if (leftExpression instanceof BinaryExpression<?> binaryExpression) {
                PersistentPropertyPath propertyPath;
                Expression<?> otherExpression;
                if (binaryExpression.getLeft() instanceof io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> leftProp) {
                    propertyPath = leftProp.getPropertyPath();
                    otherExpression = binaryExpression.getRight();
                } else if (binaryExpression.getRight() instanceof io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> rightProp) {
                    propertyPath = rightProp.getPropertyPath();
                    otherExpression = binaryExpression.getLeft();
                } else {
                    throw new IllegalStateException("Unsupported expression type: " + binaryExpression);
                }
                if (binaryExpression.getType() == BinaryExpressionType.PROD) {
                    query.put("$expr", Map.of(
                        op,
                        asList(
                            Map.of("$multiply", List.of(
                                "$" + propertyPath.getPath(),
                                valueRepresentation(queryState, propertyPath, propertyPath, otherExpression)
                            )),
                            valueRepresentation(queryState, propertyPath, propertyPath, value)
                        )
                    ));
                    return;
                }
                throw new IllegalStateException("Unsupported binary expression type: " + binaryExpression.getType());
            }
            if (leftExpression instanceof UnaryExpression<?> unaryExpression) {
                PersistentPropertyPath propertyPath = CriteriaUtils.requireProperty(unaryExpression.getExpression()).getPropertyPath();
                if (unaryExpression.getType() == UnaryExpressionType.LENGTH) {
                    query.put("$expr", Map.of(
                        op,
                        asList(
                            Map.of(STR_LEN_CP, "$" + propertyPath.getPath()),
                            valueRepresentation(queryState, propertyPath, propertyPath, value)
                        )
                    ));
                    return;
                }
                throw new IllegalStateException("Unsupported unary expression type: " + unaryExpression.getType());
            }
            PersistentPropertyPath propertyPath = CriteriaUtils.requireProperty(leftExpression).getPropertyPath();
            appendOperatorExpression(op, value, propertyPath);
        }

        private void appendOperatorExpression(String op, Expression<?> value, PersistentPropertyPath propertyPath) {
            if (value instanceof io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> persistentPropertyPath) {
                PersistentPropertyPath p2 = getRequiredProperty(persistentPropertyPath);
                query.put("$expr", Map.of(
                    op,
                    asList(
                        "$" + propertyPath.getPath(), "$" + p2.getPath()
                    )
                ));
                return;
            }
            PersistentEntityUtils.traversePersistentProperties(propertyPath, (associations, property) -> {
                String path = asPath(associations, property);
                query.put(path, Collections.singletonMap(op, valueRepresentation(queryState, propertyPath, PersistentPropertyPath.of(associations, property), value)));
            });
        }

        private void visitPredicate(IExpression<Boolean> expression) {
            if (expression instanceof IPredicate predicateVisitable) {
                predicateVisitable.visitPredicate(this);
            } else if (expression instanceof io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?>) {
                visitIsTrue(expression);
            } else {
                throw new IllegalStateException("Unknown boolean expression: " + expression);
            }
        }

        @Override
        public void visit(ConjunctionPredicate conjunction) {
            Collection<? extends IExpression<Boolean>> predicates = conjunction.getPredicates();
            if (predicates.isEmpty()) {
                return;
            }
            if (predicates.size() == 1) {
                visitPredicate(predicates.iterator().next());
                return;
            }
            List<Object> ops = new ArrayList<>(predicates.size());
            query.put("$and", ops);
            visitConjunctionPredicate(predicates, ops);
        }

        private void visitConjunctionPredicate(Collection<? extends IExpression<Boolean>> predicates, List<Object> ops) {
            for (IExpression<Boolean> expression : predicates) {
                if (expression instanceof ConjunctionPredicate conjunctionPredicate) {
                    visitConjunctionPredicate(conjunctionPredicate.getPredicates(), ops);
                } else {
                    Map<String, Object> preQuery = query;
                    query = new LinkedHashMap<>();
                    ops.add(query);
                    visitPredicate(expression);
                    query = preQuery;
                }
            }
        }

        @Override
        public void visit(DisjunctionPredicate disjunction) {
            Collection<? extends IExpression<Boolean>> predicates = disjunction.getPredicates();
            if (predicates.isEmpty()) {
                return;
            }
            if (predicates.size() == 1) {
                visitPredicate(predicates.iterator().next());
                return;
            }
            List<Object> ops = new ArrayList<>(predicates.size());
            query.put("$or", ops);
            boolean wasInDisjunction = inDisjunction;
            inDisjunction = true;
            visitDisjunctionPredicate(predicates, ops);
            inDisjunction = wasInDisjunction;
        }

        private Object scalarValue(Expression<?> expression) {
            if (expression instanceof BindingParameter bindingParameter) {
                return Map.of(QUERY_PARAMETER_PLACEHOLDER, queryState.pushParameter(bindingParameter, newBindingContext(null)));
            }
            if (expression instanceof LiteralExpression<?> literalExpression) {
                return Objects.requireNonNull(literalExpression.getValue(), "A score restriction needs a value");
            }
            throw new IllegalStateException("Unsupported expression: " + expression);
        }

        private void visitDisjunctionPredicate(Collection<? extends IExpression<Boolean>> predicates, List<Object> ops) {
            for (IExpression<Boolean> expression : predicates) {
                Map<String, Object> preQuery = query;
                query = new LinkedHashMap<>();
                ops.add(query);
                if (expression instanceof DisjunctionPredicate disjunctionPredicate) {
                    visitDisjunctionPredicate(disjunctionPredicate.getPredicates(), ops);
                } else {
                    visitPredicate(expression);
                }
                query = preQuery;
            }
        }

        @Override
        public void visit(NegatedPredicate negate) {
            IExpression<Boolean> negated = negate.getNegated();
            if (negated instanceof InPredicate<?> p) {
                visitIn(p.getExpression(), p.getValues(), true);
                return;
            }
            if (negated instanceof BetweenPredicate betweenPredicate) {
                visitInBetween(betweenPredicate.getValue(), betweenPredicate.getFrom(), betweenPredicate.getTo(), true);
                return;
            }
            visitPredicate(negate.getNegated());
            negate(query);
        }

        private void negate(Map<String, Object> map) {
            Set<Map.Entry<String, Object>> entries = map.entrySet();
            if (entries.size() != 1) {
                throw new IllegalStateException("Expected one entry got: " + map);
            }
            Map.Entry<String, Object> propertyPredicate = entries.iterator().next();
            String key = propertyPredicate.getKey();
            Object value = propertyPredicate.getValue();
            if ("$or".equals(key)) {
                map.remove("$or");
                map.put("$nor", value);
            } else if ("$and".equals(key)) {
                map.put("$nor", List.of(Map.of("$and", map.remove("$and"))));
            } else {
                map.put(key, Map.of("$not", value));
            }
        }

        @Override
        public PersistentPropertyPath getRequiredProperty(io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> persistentPropertyPath) {
            return persistentPropertyPath.getPropertyPath();
        }

        @Override
        public void visitIn(Expression<?> expression, Collection<?> values, boolean negated) {
            query.put(
                getPropertyPersistName(CriteriaUtils.requireProperty(expression).getPropertyPath()),
                Map.of(negated ? "$nin" : "$in", values.stream().map(val -> valueRepresentation(queryState, expression, val)).toList())
            );
        }

        @Override
        public void visitRegexp(Expression<?> leftExpression, Expression<?> expression) {
            Expression<?> value = expression;
            if (expression instanceof LiteralExpression<?> literalExpression) {
                value = new LiteralExpression<Object>(new RegexPattern(Objects.requireNonNull(literalExpression.getValue()).toString()));
            }
            appendOperatorExpression(leftExpression, REGEX, value);
        }

        @Override
        public void visitContains(Expression<?> leftExpression, Expression<?> rightExpression, boolean ignoreCase) {
            handleRegexExpression(leftExpression, ignoreCase, false, false, false, rightExpression, false);
        }

        @Override
        public void visitEndsWith(Expression<?> leftExpression, Expression<?> rightExpression, boolean ignoreCase) {
            handleRegexExpression(leftExpression, ignoreCase, false, false, true, rightExpression, false);
        }

        @Override
        public void visitStartsWith(Expression<?> leftExpression, Expression<?> rightExpression, boolean ignoreCase) {
            handleRegexExpression(leftExpression, ignoreCase, false, true, false, rightExpression, false);
        }

        @Override
        public void visit(LikePredicate likePredicate) {
            Expression<String> pattern = likePredicate.getPattern();
            if (pattern instanceof LiteralExpression<?> literalExpression && literalExpression.getValue() instanceof String patternString) {
                patternString = patternString
                    .replace("_", ".")
                    .replace("%", ".*");
                pattern = new LiteralExpression<>("^" + patternString + "$");
            }
            handleRegexExpression(
                likePredicate.getExpression(),
                likePredicate.isCaseInsensitive(), likePredicate.isNegated(), false, false,
                pattern, true);
        }

        @Override
        public void visit(ExistsSubqueryPredicate existsSubqueryPredicate) {
            throw new UnsupportedOperationException("ExistsSubquery is not supported by this implementation.");
        }

        @Override
        public void visitEquals(Expression<?> leftExpression, Expression<?> rightExpression, boolean ignoreCase) {
            if (leftExpression instanceof io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> persistentPropertyPath) {
                PersistentPropertyPath propertyPath = persistentPropertyPath.getPropertyPath();
                PersistentProperty property = propertyPath.getProperty();
                if (property.isEnum() && rightExpression instanceof LiteralExpression<?> literalExpression
                    && literalExpression.getValue() instanceof String stringValue) {
                    String typeName = property.getTypeName().replace("$", ".");
                    if (stringValue.startsWith(typeName)) {
                        for (PersistentProperty.EnumConstant enumConstant : property.getEnumConstants()) {
                            if (stringValue.equals(typeName + "." + enumConstant.name())) {
                                if (property.getDataType() == DataType.STRING) {
                                    rightExpression = new LiteralExpression<Object>(enumConstant.name());
                                }
                                if (property.getDataType() == DataType.INTEGER) {
                                    rightExpression = new LiteralExpression<Object>(enumConstant.ordinal());
                                }
                                break;
                            }
                        }
                    }
                }
            }
            if (ignoreCase) {
                handleRegexExpression(leftExpression, true, false, true, true, rightExpression, false);
                return;
            }
            appendEquals(leftExpression, rightExpression);
        }

        private void appendEquals(Expression<?> leftExpression, Expression<?> value) {
            appendOperatorExpression(leftExpression, "$eq", value);
        }

        @Override
        public void visitNotEquals(Expression<?> leftExpression, Expression<?> rightExpression, boolean ignoreCase) {
            if (ignoreCase) {
                handleRegexExpression(leftExpression, true, true, true, true, rightExpression, false);
                return;
            }
            appendPropertyNotEquals(leftExpression, rightExpression);
        }

        private void appendPropertyNotEquals(Expression<?> leftExpression, Expression<?> value) {
            appendOperatorExpression(leftExpression, "$ne", value);
        }

        @Override
        public void visitGreaterThan(Expression<?> leftExpression, Expression<?> rightExpression) {
            appendOperatorExpression(leftExpression, "$gt", rightExpression);
        }

        @Override
        public void visitGreaterThanOrEquals(Expression<?> leftExpression, Expression<?> rightExpression) {
            appendOperatorExpression(leftExpression, "$gte", rightExpression);
        }

        @Override
        public void visitLessThan(Expression<?> leftExpression, Expression<?> rightExpression) {
            appendOperatorExpression(leftExpression, "$lt", rightExpression);
        }

        @Override
        public void visitLessThanOrEquals(Expression<?> leftExpression, Expression<?> rightExpression) {
            appendOperatorExpression(leftExpression, "$lte", rightExpression);
        }

        @Override
        public void visitInBetween(Expression<?> value, Expression<?> from, Expression<?> to, boolean negated) {
            String propertyName;
            Object firstCondition;
            Object secondCondition;
            if (value instanceof UnaryExpression<?> valueExp && valueExp.getType() == UnaryExpressionType.LOWER
                && from instanceof UnaryExpression<?> fromExp && fromExp.getType() == UnaryExpressionType.LOWER
                && to instanceof UnaryExpression<?> toExp && toExp.getType() == UnaryExpressionType.LOWER) {
                PersistentPropertyPath propertyPath = requireProperty(valueExp.getExpression()).getPropertyPath();
                propertyName = getPropertyPersistName(propertyPath);
                firstCondition = Map.of(TO_LOWER, valueRepresentation(queryState, propertyPath, fromExp.getExpression()));
                secondCondition = Map.of(TO_LOWER, valueRepresentation(queryState, propertyPath, toExp.getExpression()));
            } else {
                PersistentPropertyPath propertyPath = requireProperty(value).getPropertyPath();
                propertyName = getPropertyPersistName(propertyPath);
                firstCondition = valueRepresentation(queryState, propertyPath, from);
                secondCondition = valueRepresentation(queryState, propertyPath, to);
            }
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            map.put("$gte", firstCondition);
            map.put("$lte", secondCondition);
            if (negated) {
                query.put(
                    propertyName, Map.of("$not", map)
                );
            } else {
                query.put(
                    propertyName, map
                );
            }
        }

        @Override
        public void visitIsFalse(Expression<?> expression) {
            appendEquals(expression, new LiteralExpression<>(false));
        }

        @Override
        public void visitIsNotNull(Expression<?> expression) {
            appendPropertyNotEquals(expression, new LiteralExpression<>((Object) null));
        }

        @Override
        public void visitIsNull(Expression<?> expression) {
            appendEquals(expression, new LiteralExpression<>((Object) null));
        }

        @Override
        public void visitIsTrue(Expression<?> expression) {
            appendEquals(expression, new LiteralExpression<>(true));
        }

        @Override
        public void visitIsEmpty(Expression<?> expression) {
            String propertyName = getPropertyPersistName(CriteriaUtils.requireProperty(expression).getPropertyPath());
            query.put("$or", asList(
                Map.of(propertyName, Map.of("$eq", "")),
                Map.of(propertyName, Map.of("$exists", false))
            ));
        }

        @Override
        public void visitIsNotEmpty(Expression<?> expression) {
            String propertyName = getPropertyPersistName(CriteriaUtils.requireProperty(expression).getPropertyPath());
            query.put("$and", asList(
                Map.of(propertyName, Map.of("$ne", "")),
                Map.of(propertyName, Map.of("$exists", true))
            ));
        }

        @Override
        public void visitArrayContains(Expression<?> leftExpression, Expression<?> expression) {
            Object value = expression;
            if (expression instanceof LiteralExpression<?> literalExpression) {
                value = literalExpression.getValue();
            }
            Object criteriaValue;
            if (value instanceof Iterable<?> iterable) {
                List<?> values = CollectionUtils.iterableToList(iterable);
                criteriaValue = values.stream().map(val -> valueRepresentation(queryState, leftExpression, val)).toList();
            } else {
                criteriaValue = List.of(valueRepresentation(queryState, leftExpression, value));
            }
            PersistentPropertyPath propertyPath = requireProperty(leftExpression).getPropertyPath();
            query.put(getPropertyPersistName(propertyPath), Map.of("$all", criteriaValue));
        }

        @Override
        public void visitGeoWithin(Expression<?> leftExpression, Expression<?> expression) {
            PersistentPropertyPath propertyPath = requireProperty(leftExpression).getPropertyPath();
            query.put(getPropertyPersistName(propertyPath), Map.of(
                "$geoWithin", Map.of(
                    GEOMETRY_OPERATOR, valueRepresentation(queryState, propertyPath, expression)
                )
            ));
        }

        @Override
        public void visitGeoIntersects(Expression<?> leftExpression, Expression<?> expression) {
            PersistentPropertyPath propertyPath = requireProperty(leftExpression).getPropertyPath();
            query.put(getPropertyPersistName(propertyPath), Map.of(
                "$geoIntersects", Map.of(
                    GEOMETRY_OPERATOR, valueRepresentation(queryState, propertyPath, expression)
                )
            ));
        }

        @Override
        public void visit(NearPredicate nearPredicate) {
            visitNear(nearPredicate.getValue(), nearPredicate.getGeometry(), nearPredicate.getDistance());
        }

        @Override
        public void visitTextMatch(List<Expression<?>> fields, Expression<String> queryExpression, MatchMode mode) {
            if (usesSearchIndex(persistentEntity)) {
                visitSearchIndexMatch(fields, queryExpression, mode);
                return;
            }
            if (mode == MatchMode.PREFIX) {
                throw new UnsupportedSearchFeatureException(SearchCapability.PREFIX, "MongoDB $text");
            }
            if (mode == MatchMode.FUZZY) {
                throw new UnsupportedSearchFeatureException(SearchCapability.FUZZY, "MongoDB $text");
            }
            if (!(queryExpression instanceof BindingParameter bindingParameter)) {
                throw new IllegalStateException("A full-text search query must be a parameter, got: " + queryExpression);
            }
            // $text always searches the collection's text index; the fields only decide the language
            String converter = MongoTextSearchConverter.converterFor(mode);
            BindingParameter converted = context -> TextSearchSupport.withConverter(bindingParameter.bind(context), converter);
            int index = queryState.pushParameter(converted, newBindingContext(null));
            Map<String, Object> text = new LinkedHashMap<>();
            text.put("$search", Map.of(QUERY_PARAMETER_PLACEHOLDER, index));
            text.put("$language", TextSearchSupport.resolveLanguage(persistentEntity));
            query.put("$text", text);
        }

        /**
         * Renders a search predicate for an entity with a {@code @MongoSearchIndex} as the {@code $search}
         * stage recorded on the query state; the operator is rendered at runtime, see {@link #SEARCH_OPERATOR_PLACEHOLDER}.
         */
        private void visitSearchIndexMatch(List<Expression<?>> fields, Expression<String> queryExpression, MatchMode mode) {
            if (!(queryExpression instanceof BindingParameter bindingParameter)) {
                throw new IllegalStateException("A full-text search query must be a parameter, got: " + queryExpression);
            }
            if (queryState.searchOperator != null) {
                throw new UnsupportedOperationException("MongoDB $search supports one search predicate per query.");
            }
            if (inDisjunction) {
                throw new UnsupportedOperationException("MongoDB $search cannot be combined with OR, as the $search stage filters every document.");
            }
            List<String> paths = new ArrayList<>(fields.size());
            Map<String, Object> boosts = new LinkedHashMap<>();
            for (Expression<?> field : fields) {
                PersistentPropertyPath propertyPath = requireProperty(field).getPropertyPath();
                String path = getPropertyPersistName(propertyPath);
                paths.add(path);
                int weight = propertyPath.getProperty().getAnnotationMetadata()
                    .enumValue(SearchField.class, SearchField.MEMBER_WEIGHT, SearchWeight.class)
                    .map(SearchWeight::numericWeight)
                    .orElse(1);
                if (weight != 1) {
                    boosts.put(path, weight);
                }
            }
            int index = queryState.pushParameter(bindingParameter, newBindingContext(null));
            Map<String, Object> operator = new LinkedHashMap<>();
            operator.put(QUERY_PARAMETER_PLACEHOLDER, index);
            operator.put(SEARCH_OPERATOR_PLACEHOLDER, mode.name());
            operator.put(SEARCH_PATHS, paths);
            if (!boosts.isEmpty()) {
                operator.put(SEARCH_BOOSTS, boosts);
            }
            queryState.searchOperator = operator;
        }

        @Override
        public void visitNear(Expression<?> leftExpression, Expression<?> geometryExpression, Expression<? extends Number> distanceExpression) {
            PersistentPropertyPath propertyPath = requireProperty(leftExpression).getPropertyPath();
            query.put(getPropertyPersistName(propertyPath), Map.of(
                "$near", Map.of(
                    GEOMETRY_OPERATOR, valueRepresentation(queryState, propertyPath, geometryExpression),
                    "$maxDistance", valueRepresentation(queryState, propertyPath, propertyPath, distanceExpression)
                )
            ));
        }

        @Override
        public void visitIdEquals(Expression<?> expression) {
            if (persistentEntity.hasCompositeIdentity()) {
                throw new IllegalStateException("Composite ID not supported!");
            } else if (persistentEntity.hasIdentity()) {
                query.put(
                    MONGO_ID_FIELD,
                    valueRepresentation(queryState, new PersistentPropertyPath(List.of(), Objects.requireNonNull(persistentEntity.getIdentity())), expression)
                );
            } else {
                throw new IllegalStateException("No ID found for entity: " + persistentEntity.getName());
            }
        }

        private void handleRegexExpression(Expression<?> leftExpression,
                                           boolean ignoreCase,
                                           boolean negate,
                                           boolean startsWith,
                                           boolean endsWith,
                                           Expression<?> value,
                                           boolean isLike) {
            PersistentPropertyPath propertyPath = CriteriaUtils.requireProperty(leftExpression).getPropertyPath();
            Object filterValue;
            Map<String, Object> regexCriteria = new LinkedHashMap<>(2);
            String options = ignoreCase ? "i" : "";
            if (isLike) {
                options += "l";
            }
            regexCriteria.put(OPTIONS, options);
            String regexValue;
            if (value instanceof BindingParameter bindingParameter) {
                int index = queryState.pushParameter(
                    bindingParameter,
                    newBindingContext(propertyPath, propertyPath)
                );
                regexValue = QUERY_PARAMETER_PLACEHOLDER + ":" + index;
            } else if (value instanceof LiteralExpression<?> literalExpression) {
                regexValue = (String) literalExpression.getValue();
            } else {
                regexValue = value.toString();
            }
            StringBuilder regexValueBuff = new StringBuilder();
            if (startsWith) {
                regexValueBuff.append("^");
            }
            regexValueBuff.append(regexValue);
            if (endsWith) {
                regexValueBuff.append("$");
            }
            regexCriteria.put(REGEX, regexValueBuff.toString());
            if (negate) {
                filterValue = Map.of(NOT, regexCriteria);
            } else {
                filterValue = regexCriteria;
            }
            query.put(getPropertyPersistName(propertyPath), filterValue);
        }

        @Nullable
        private Object valueRepresentation(PropertyParameterCreator parameterCreator, Expression<?> leftExpression, @Nullable Object value) {
            PersistentPropertyPath propertyPath = requireProperty(leftExpression).getPropertyPath();
            return valueRepresentation(parameterCreator, propertyPath, propertyPath, value);
        }

        @Nullable
        private Object valueRepresentation(PropertyParameterCreator parameterCreator, PersistentPropertyPath propertyPath, @Nullable Object value) {
            return valueRepresentation(parameterCreator, propertyPath, propertyPath, value);
        }

        @Nullable
        private Object valueRepresentation(PropertyParameterCreator parameterCreator,
                                           PersistentPropertyPath inPropertyPath,
                                           PersistentPropertyPath outPropertyPath,
                                           @Nullable
                                           Object value) {
            if (value instanceof LiteralExpression<?> literalExpression) {
                value = literalExpression.getValue();
            }
            if (value instanceof RegexPattern regexPattern) {
                return "'" + Pattern.quote(regexPattern.value) + "'";
            }
            if (value instanceof LocalDate localDate) {
                return Map.of(MONGO_DATE_IDENTIFIER, formatDate(localDate));
            }
            if (value instanceof LocalDateTime localDateTime) {
                return Map.of(MONGO_DATE_IDENTIFIER, formatDate(localDateTime));
            }
            if (value instanceof BindingParameter bindingParameter) {
                int index = parameterCreator.pushParameter(
                    bindingParameter,
                    newBindingContext(inPropertyPath, outPropertyPath)
                );
                return Map.of(QUERY_PARAMETER_PLACEHOLDER, index);
            }
            return value;
        }

        private String formatDate(LocalDate localDate) {
            return formatDate(localDate.atStartOfDay());
        }

        private String formatDate(LocalDateTime localDateTime) {
            return formatDate(localDateTime.atZone(ZoneOffset.UTC).toInstant().toEpochMilli());
        }

        private String formatDate(final long dateTime) {
            return ZonedDateTime.ofInstant(Instant.ofEpochMilli(dateTime), ZoneOffset.UTC).format(ISO_OFFSET_DATE_TIME);
        }

    }

    private final class MongoSelectionVisitor implements SelectionVisitor {

        private final QueryState queryState;
        private final Map<String, Object> projectionObj;
        private final Map<String, Object> groupObj;
        private final Map<String, Object> countObj;
        private final Map<String, Object> addFieldsObj;
        @Nullable
        private String alias;

        public MongoSelectionVisitor(QueryState queryState,
                                     Map<String, Object> projectionObj,
                                     Map<String, Object> groupObj,
                                     Map<String, Object> countObj,
                                     Map<String, Object> addFieldsObj) {
            this.queryState = queryState;
            this.projectionObj = projectionObj;
            this.groupObj = groupObj;
            this.countObj = countObj;
            this.addFieldsObj = addFieldsObj;
        }

        @Override
        public void visit(io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> persistentPropertyPath) {
            PersistentProperty property = persistentPropertyPath.getProperty();
            String propertyPersistName = getPropertyPersistName(property);
            projectionObj.put(propertyPersistName, 1);
        }

        @Override
        public void visit(AliasedSelection<?> aliasedSelection) {
            alias = aliasedSelection.getAlias();
            aliasedSelection.getSelection().visitSelection(this);
            alias = null;
        }

        @Override
        public void visit(PersistentEntityRoot<?> entityRoot) {
            // The default is the entity projection
        }

        @Override
        public void visit(PersistentEntitySubquery<?> subquery) {
            throw new IllegalStateException("Subquery not supported by MongoDB");
        }

        @Override
        public void visit(CompoundSelection<?> compoundSelection) {
            for (Selection<?> selection : compoundSelection.getCompoundSelectionItems()) {
                if (selection instanceof ISelection<?> selectionVisitable) {
                    selectionVisitable.visitSelection(this);
                } else {
                    throw new IllegalStateException("Unknown selection object: " + selection);
                }
            }
        }

        @Override
        public void visit(LiteralExpression<?> literalExpression) {
            projectionObj.put("val", Map.of("$literal", asLiteral(literalExpression.getValue())));
        }

        @Override
        public void visit(UnaryExpression<?> unaryExpression) {
            Expression<?> expression = unaryExpression.getExpression();
            switch (unaryExpression.getType()) {
                case SUM, AVG, MAX, MIN -> {
                    PersistentPropertyPath propertyPath = requireProperty(expression).getPropertyPath();
                    switch (unaryExpression.getType()) {
                        case SUM -> addProjection(groupObj, "$sum", propertyPath);
                        case AVG -> addProjection(groupObj, "$avg", propertyPath);
                        case MAX -> addProjection(groupObj, "$max", propertyPath);
                        case MIN -> addProjection(groupObj, "$min", propertyPath);
                        default ->
                            throw new IllegalStateException("Unsupported expression type: " + unaryExpression.getExpression());
                    }
                }
                case COUNT -> {
                    // before adding support for count distinct in https://github.com/micronaut-projects/micronaut-data/issues/2695
                    // it was producing the same query as this, same as count basically
                    countObj.put("$count", "result");
                }
                case COUNT_DISTINCT -> {
                    if (expression instanceof PersistentEntityRoot) {
                        // before adding support for count distinct in https://github.com/micronaut-projects/micronaut-data/issues/2695
                        // it was producing the same query as this, same as count basically
                        countObj.put("$count", "result");
                    } else if (expression instanceof io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?>) {
                        throw new UnsupportedOperationException("Count distinct against property is not supported by Micronaut Data MongoDB.");
                    } else {
                        throw new IllegalStateException("Illegal expression: " + expression + " for count distinct selection!");
                    }
                }
                default ->
                    throw new IllegalStateException("Unsupported expression type: " + unaryExpression.getExpression());
            }
        }

        @Override
        public void visit(IdExpression<?, ?> idExpression) {
            projectionObj.put(MONGO_ID_FIELD, 1);
        }

        private void addProjection(Map<String, Object> groupBy, String op, PersistentPropertyPath propertyPath) {
            groupBy.put(alias == null ? propertyPath.getProperty().getName() : alias, Map.of(op, "$" + propertyPath.getPath()));
        }

        @Override
        public void visit(FunctionExpression<?> functionExpression) {
            if (TextSearchSupport.SCORE_FUNCTION.equals(functionExpression.getName())) {
                String scoreField = alias == null ? TextSearchSupport.SCORE_ALIAS : alias;
                if (usesSearchIndex(queryState.getEntity())) {
                    queryState.searchScoreFields.add(scoreField);
                } else {
                    // The relevance score rides along the entity as an added field
                    addFieldsObj.put(scoreField, TEXT_SCORE);
                }
                return;
            }
            if (TextSearchSupport.HIGHLIGHT_FUNCTION.equals(functionExpression.getName())) {
                if (!usesSearchIndex(queryState.getEntity())) {
                    throw new UnsupportedSearchFeatureException(SearchCapability.HIGHLIGHT, "MongoDB $text");
                }
                PersistentPropertyPath propertyPath = requireProperty(functionExpression.getExpressions().get(0)).getPropertyPath();
                String highlightAlias = alias == null ? TextSearchSupport.highlightAlias(propertyPath.getProperty().getName()) : alias;
                queryState.highlights.put(highlightAlias, getPropertyPersistName(propertyPath));
                return;
            }
            if (VectorScoringDialectSupport.SCORE_FUNCTION.equals(functionExpression.getName())) {
                registerVectorSearch(queryState, functionExpression);
                queryState.vectorScoreFields.add(alias == null ? TextSearchSupport.SCORE_ALIAS : alias);
                return;
            }
            throw new UnsupportedOperationException("Function expression is not supported by Micronaut Data MongoDB.");
        }

        @Override
        public void visit(CastExpression<?> castExpression) {
            throw new UnsupportedOperationException("CAST is not supported by Micronaut Data MongoDB.");
        }

        @Override
        public void visit(CurrentTemporalExpression<?> currentTemporalExpression) {
            throw new UnsupportedOperationException("Current temporal expression is not supported by Micronaut Data MongoDB.");
        }

        @Override
        public void visit(BinaryExpression<?> binaryExpression) {
            throw new UnsupportedOperationException("Binary expression: " + binaryExpression + " is not supported by Micronaut Data MongoDB.");
        }
    }
}
