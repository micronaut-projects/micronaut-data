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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.document.mongo.MongoAnnotations;
import io.micronaut.data.exceptions.MappingException;
import io.micronaut.data.model.Association;
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
import io.micronaut.data.model.jpa.criteria.impl.expression.BinaryExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.FunctionExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.IdExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.LiteralExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.UnaryExpression;
import io.micronaut.data.model.jpa.criteria.impl.predicate.ConjunctionPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.DisjunctionPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.ExistsSubqueryPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.LikePredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.NegatedPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.InPredicate;
import io.micronaut.data.model.jpa.criteria.impl.selection.AliasedSelection;
import io.micronaut.data.model.jpa.criteria.impl.selection.CompoundSelection;
import io.micronaut.data.model.naming.NamingStrategy;
import io.micronaut.data.model.query.BindingParameter;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.query.builder.QueryBuilder2;
import io.micronaut.data.model.query.builder.QueryParameterBinding;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.model.query.impl.AdvancedPredicateVisitor;
import io.micronaut.serde.config.annotation.SerdeConfig;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Selection;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
@TypeHint(MongoQueryBuilder2.class)
@Internal
public final class MongoQueryBuilder2 implements QueryBuilder2 {

    /**
     * An object with this property is replaced with an actual query parameter at the runtime.
     */
    public static final String QUERY_PARAMETER_PLACEHOLDER = "$mn_qp";
    public static final String MONGO_DATE_IDENTIFIER = "$date";
    public static final String MONGO_ID_FIELD = "_id";
    private static final String REGEX = "$regex";
    private static final String NOT = "$not";
    private static final String OPTIONS = "$options";

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

        addLookups(selectQueryDefinition.getJoinPaths(), queryState);
        List<Map<String, Object>> pipeline = queryState.rootLookups.pipeline;
        buildProjection(selectQueryDefinition.selection(), group, projectionObj, countObj);
        Predicate predicate = selectQueryDefinition.predicate();
        if (predicate != null) {
            predicateObj = buildWhereClause(predicate, queryState);
        }

        if (!predicateObj.isEmpty()) {
            pipeline.add(Map.of("$match", predicateObj));
        }
        if (!group.isEmpty()) {
            group.put(MONGO_ID_FIELD, null);
            pipeline.add(Map.of("$group", group));
        }
        if (!countObj.isEmpty()) {
            pipeline.add(countObj);
        }
        if (!projectionObj.isEmpty()) {
            pipeline.add(Map.of("$project", projectionObj));
        } else {
            String customProjection = annotationMetadata.stringValue(MongoAnnotations.PROJECTION).orElse(null);
            if (customProjection != null) {
                pipeline.add(Map.of("$project", new RawJsonValue(customProjection)));
            }
        }
        List<Order> orders = selectQueryDefinition.order();
        if (!orders.isEmpty()) {
            Map<String, Object> sortObj = new LinkedHashMap<>();
            orders.forEach(order -> {
                io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> persistentPropertyPath = requireProperty(order.getExpression());
                sortObj.put(persistentPropertyPath.getPathAsString(), order.isAscending() ? 1 : -1);
            });
            pipeline.add(Map.of("$sort", sortObj));
        } else {
            String customSort = annotationMetadata.stringValue(MongoAnnotations.SORT).orElse(null);
            if (customSort != null) {
                pipeline.add(Map.of("$sort", new RawJsonValue(customSort)));
            }
        }
        if (selectQueryDefinition.offset() > 0) {
            pipeline.add(Map.of("$skip", selectQueryDefinition.offset()));
        }
        if (selectQueryDefinition.limit() != -1) {
            pipeline.add(Map.of("$limit", selectQueryDefinition.limit()));
        }

        String q;
        if (pipeline.isEmpty()) {
            q = "{}";
        } else if (isMatchOnlyStage(pipeline)) {
            q = toJsonString(predicateObj);
        } else {
            q = toJsonString(pipeline);
        }
        return QueryResult.of(q, queryState.getParameterBindings());
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
                if (currentLookup.subLookups.containsKey(thisPath)) {
                    currentLookup = currentLookup.subLookups.get(path);
                    currentEntityPath = new StringJoiner(".");
                    continue;
                }

                PersistentPropertyPath propertyPath = currentLookup.persistentEntity.getPropertyPath(thisPath);
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
                    PersistentProperty identity = associatedEntity.getIdentity();
                    if (identity == null) {
                        throw new IllegalArgumentException("Associated entity [" + associatedEntity.getName() + "] defines no ID. Cannot join.");
                    }
                    final PersistentProperty associatedId = associationOwner.getIdentity();
                    if (associatedId == null) {
                        throw new MappingException("Cannot join on entity [" + associationOwner.getName() + "] that has no declared ID");
                    }
                    Association owningAssociation = inverseSide.orElse(association);
                    boolean isAssociationOwner = !association.getInverseSide().isPresent();
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
                        PersistentProperty identity = lookupStage.persistentEntity.getIdentity();
                        if (identity == null) {
                            throw new IllegalStateException("Null identity of persistent entity: " + lookupStage.persistentEntity);
                        }
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
        PersistentProperty identity = entity.getIdentity();
        if (identity == null) {
            throw new MappingException("Cannot have a foreign key association without an ID on entity: " + entity.getName());
        }
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
        return query;
    }

    private void buildProjection(Selection<?> selection,
                                 Map<String, Object> groupObj,
                                 Map<String, Object> projectionObj,
                                 Map<String, Object> countObj) {
        if (selection == null) {
            return;
        }
        if (selection instanceof ISelection<?> selectionVisitable) {
            selectionVisitable.visitSelection(new MongoSelectionVisitor(projectionObj, groupObj, countObj));
        } else {
            throw new IllegalStateException("Unsupported selection type: " + selection.getClass().getName());
        }
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
                if (association != joinAssociation.getAssociatedEntity().getIdentity()) {
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
                if (property != joinAssociation.getAssociatedEntity().getIdentity()) {
                    String joinStringPath = joinPathJoiner.toString();
                    if (!queryState.isJoined(joinStringPath)) {
                        throw new IllegalArgumentException("Property is not joined at path: " + joinStringPath);
                    }
                }
                // We don't need to join to access the id of the relation
            }
        } else if (TypeRole.ID.equals(name) && entity.getIdentity() != null) {
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
            predicateQuery = toJsonString(
                buildWhereClause(predicate, queryState)
            );
        } else {
            predicateQuery = "";
        }

        Map<String, Object> propertiesToUpdate = updateQueryDefinition.propertiesToUpdate();
        Map<String, Object> sets = CollectionUtils.newLinkedHashMap(propertiesToUpdate.size());
        for (Map.Entry<String, Object> e : propertiesToUpdate.entrySet()) {
            PersistentPropertyPath propertyPath = findProperty(queryState, e.getKey());
            String propertyPersistName = getPropertyPersistName(propertyPath);
            if (e.getValue() instanceof BindingParameter bindingParameter) {
                int index = queryState.pushParameter(
                    bindingParameter,
                    newBindingContext(propertyPath)
                );
                sets.put(propertyPersistName, Map.of(QUERY_PARAMETER_PLACEHOLDER, index));
            } else {
                sets.put(propertyPersistName, e.getValue());
            }
        }

        String update = toJsonString(Map.of("$set", sets));

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
            predicateQuery = toJsonString(
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

    private String toJsonString(Object obj) {
        StringBuilder sb = new StringBuilder();
        append(sb, obj);
        return sb.toString();
    }

    private void appendMap(StringBuilder sb, Map<String, Object> map) {
        sb.append("{");
        for (Iterator<Map.Entry<String, Object>> iterator = map.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<String, Object> e = iterator.next();
            String key = e.getKey();
            Object value = e.getValue();
            if (!skipValue(value)) {
                if (shouldEscapeKey(key)) {
                    sb.append("'").append(key).append("'");
                } else {
                    sb.append(key);
                }
                sb.append(":");
                append(sb, value);
                if (iterator.hasNext()) {
                    sb.append(",");
                }
            }
        }
        sb.append("}");
    }

    private boolean skipValue(Object obj) {
        if (obj instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        if (obj instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        return false;
    }

    private void appendArray(StringBuilder sb, Collection<?> collection) {
        sb.append("[");
        for (Iterator<?> iterator = collection.iterator(); iterator.hasNext(); ) {
            Object value = iterator.next();
            append(sb, value);
            if (iterator.hasNext()) {
                sb.append(",");
            }
        }
        sb.append("]");
    }

    private void append(StringBuilder sb, Object obj) {
        if (obj instanceof Map map) {
            appendMap(sb, map);
        } else if (obj instanceof Collection<?> collection) {
            appendArray(sb, collection);
        } else if (obj instanceof RawJsonValue rawJsonValue) {
            sb.append(rawJsonValue.value);
        } else if (obj == null) {
            sb.append("null");
        } else if (obj instanceof Boolean) {
            sb.append(obj.toString().toLowerCase(Locale.ROOT));
        } else if (obj instanceof Number) {
            sb.append(obj);
        } else {
            sb.append('\'').append(obj).append('\'');
        }
    }

    private boolean shouldEscapeKey(String s) {
        for (char c : s.toCharArray()) {
            if (!Character.isAlphabetic(c) && !Character.isDigit(c) && c != '$' && c != '_') {
                return true;
            }
        }
        return false;
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
        if (property.getOwner().getIdentity() == property) {
            return MONGO_ID_FIELD;
        }
        return property.getAnnotationMetadata()
            .stringValue(SerdeConfig.class, SerdeConfig.PROPERTY)
            .orElseGet(propertyPath::getPath);
    }

    private String getPropertyPersistName(PersistentProperty property) {
        if (property.getOwner().getIdentity() == property) {
            return MONGO_ID_FIELD;
        }
        return property.getAnnotationMetadata()
            .stringValue(SerdeConfig.class, SerdeConfig.PROPERTY)
            .orElseGet(property::getName);
    }

    private Object asLiteral(@Nullable Object value) {
        if (value instanceof RegexPattern regexPattern) {
            return "'" + Pattern.quote(regexPattern.value) + "'";
        }
        return value;
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
        public int pushParameter(@NonNull BindingParameter bindingParameter, @NonNull BindingParameter.BindingContext bindingContext) {
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
                          @NonNull BindingParameter.BindingContext bindingContext);

    }

    private record RegexPattern(String value) {
    }

    private record RawJsonValue(String value) {
    }

    private class MongoPredicateVisitor implements AdvancedPredicateVisitor<PersistentPropertyPath> {

        private final PersistentEntity persistentEntity;
        private final QueryState queryState;
        private Map<String, Object> query;

        public MongoPredicateVisitor(QueryState queryState, Map<String, Object> query) {
            this.queryState = queryState;
            this.query = query;
            persistentEntity = queryState.getEntity();
        }

        private void appendOperatorExpression(Expression<?> leftExpression, String op, Object value) {
            PersistentPropertyPath propertyPath = CriteriaUtils.requireProperty(leftExpression).getPropertyPath();
            appendOperatorExpression(op, value, propertyPath);
        }

        private void appendOperatorExpression(String op, Object value, PersistentPropertyPath propertyPath) {
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
            visitDisjunctionPredicate(predicates, ops);
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
            Map<String, Object> preQuery = query;
            query = new LinkedHashMap<>();
            visitPredicate(negate.getNegated());
            if (query.size() != 1) {
                throw new IllegalStateException("Expected size of 1: Got: " + query + " " + negate.getNegated());
            }
            Map.Entry<String, Object> propertyPredicate = query.entrySet().iterator().next();
            Map<String, Object> negatedPropertyPredicate = Map.of("$not", propertyPredicate.getValue());
            query = preQuery;
            query.put(propertyPredicate.getKey(), negatedPropertyPredicate);
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
            Object value = expression;
            if (expression instanceof LiteralExpression<?> literalExpression) {
                value = new RegexPattern((String) literalExpression.getValue());
            }
            appendOperatorExpression(leftExpression, REGEX, value);
        }

        @Override
        public void visitContains(Expression<?> leftExpression, Expression<?> rightExpression, boolean ignoreCase) {
            handleRegexExpression(leftExpression, ignoreCase, false, false, false, rightExpression);
        }

        @Override
        public void visitEndsWith(Expression<?> leftExpression, Expression<?> rightExpression, boolean ignoreCase) {
            handleRegexExpression(leftExpression, ignoreCase, false, false, true, rightExpression);
        }

        @Override
        public void visitStartsWith(Expression<?> leftExpression, Expression<?> rightExpression, boolean ignoreCase) {
            handleRegexExpression(leftExpression, ignoreCase, false, true, false, rightExpression);
        }

        @Override
        public void visit(LikePredicate likePredicate) {
            if (likePredicate.isCaseInsensitive()) {
                throw new UnsupportedOperationException("ILike is not supported by this implementation.");
            }
            handleRegexExpression(
                likePredicate.getExpression(),
                false, false, false, false,
                likePredicate.getPattern());
        }

        @Override
        public void visit(ExistsSubqueryPredicate existsSubqueryPredicate) {
            throw new UnsupportedOperationException("ExistsSubquery is not supported by this implementation.");
        }

        @Override
        public void visitEquals(Expression<?> leftExpression, Expression<?> rightExpression, boolean ignoreCase) {
            if (ignoreCase) {
                handleRegexExpression(leftExpression, true, false, true, true, rightExpression);
                return;
            }
            appendEquals(leftExpression, rightExpression);
        }

        private void appendEquals(Expression<?> leftExpression, Object value) {
            appendOperatorExpression(leftExpression, "$eq", value);
        }

        @Override
        public void visitNotEquals(Expression<?> leftExpression, Expression<?> rightExpression, boolean ignoreCase) {
            if (ignoreCase) {
                handleRegexExpression(leftExpression, true, true, true, true, rightExpression);
                return;
            }
            appendPropertyNotEquals(leftExpression, rightExpression);
        }

        private void appendPropertyNotEquals(Expression<?> leftExpression, Object value) {
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
        public void visitInBetween(Expression<?> value, Expression<?> from, Expression<?> to) {
            PersistentPropertyPath propertyPath = requireProperty(value).getPropertyPath();
            String propertyName = getPropertyPersistName(propertyPath);
            query.put("$and", asList(
                Map.of(propertyName, Map.of("$gte", valueRepresentation(queryState, propertyPath, from))),
                Map.of(propertyName, Map.of("$lte", valueRepresentation(queryState, propertyPath, to)))
            ));
        }

        @Override
        public void visitIsFalse(Expression<?> expression) {
            appendEquals(expression, false);
        }

        @Override
        public void visitIsNotNull(Expression<?> expression) {
            appendPropertyNotEquals(expression, null);
        }

        @Override
        public void visitIsNull(Expression<?> expression) {
            appendEquals(expression, null);
        }

        @Override
        public void visitIsTrue(Expression<?> expression) {
            appendEquals(expression, true);
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
        public void visitIdEquals(Expression<?> expression) {
            if (persistentEntity.hasCompositeIdentity()) {
                throw new IllegalStateException("Composite ID not supported!");
            } else if (persistentEntity.hasIdentity()) {
                query.put(
                    MONGO_ID_FIELD,
                    valueRepresentation(queryState, new PersistentPropertyPath(List.of(), persistentEntity.getIdentity()), expression)
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
                                           Object value) {
            PersistentPropertyPath propertyPath = CriteriaUtils.requireProperty(leftExpression).getPropertyPath();
            Object filterValue;
            Map<String, Object> regexCriteria = new LinkedHashMap<>(2);
            regexCriteria.put(OPTIONS, ignoreCase ? "i" : "");
            String regexValue;
            if (value instanceof BindingParameter bindingParameter) {
                int index = queryState.pushParameter(
                    bindingParameter,
                    newBindingContext(propertyPath, propertyPath)
                );
                regexValue = QUERY_PARAMETER_PLACEHOLDER + ":" + index;
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

        private Object valueRepresentation(PropertyParameterCreator parameterCreator, Expression<?> leftExpression, Object value) {
            PersistentPropertyPath propertyPath = requireProperty(leftExpression).getPropertyPath();
            return valueRepresentation(parameterCreator, propertyPath, propertyPath, value);
        }

        private Object valueRepresentation(PropertyParameterCreator parameterCreator, PersistentPropertyPath propertyPath, Object value) {
            return valueRepresentation(parameterCreator, propertyPath, propertyPath, value);
        }

        private Object valueRepresentation(PropertyParameterCreator parameterCreator,
                                           PersistentPropertyPath inPropertyPath,
                                           PersistentPropertyPath outPropertyPath,
                                           Object value) {
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
            } else {
                return asLiteral(value);
            }
        }

        private String formatDate(LocalDate localDate) {
            return formatDate(localDate.atStartOfDay());
        }

        private String formatDate(LocalDateTime localDateTime) {
            return formatDate(localDateTime.atZone(ZoneId.of("Z")).toInstant().toEpochMilli());
        }

        private String formatDate(final long dateTime) {
            return ZonedDateTime.ofInstant(Instant.ofEpochMilli(dateTime), ZoneId.of("Z")).format(ISO_OFFSET_DATE_TIME);
        }

    }

    private final class MongoSelectionVisitor implements SelectionVisitor {

        private final Map<String, Object> projectionObj;
        private final Map<String, Object> groupObj;
        private final Map<String, Object> countObj;
        private String alias;

        public MongoSelectionVisitor(Map<String, Object> projectionObj, Map<String, Object> groupObj, Map<String, Object> countObj) {
            this.projectionObj = projectionObj;
            this.groupObj = groupObj;
            this.countObj = countObj;
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
            throw new UnsupportedOperationException("Function expression is not supported by Micronaut Data MongoDB.");
        }

        @Override
        public void visit(BinaryExpression<?> binaryExpression) {
            throw new UnsupportedOperationException("Binary expression: " + binaryExpression + " is not supported by Micronaut Data MongoDB.");
        }
    }
}
