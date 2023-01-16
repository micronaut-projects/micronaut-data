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
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.document.mongo.MongoAnnotations;
import io.micronaut.data.exceptions.MappingException;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.Embedded;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentEntityUtils;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.naming.NamingStrategy;
import io.micronaut.data.model.query.BindingParameter;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryParameterBinding;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.serde.config.annotation.SerdeConfig;

import javax.validation.constraints.NotNull;
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
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;

/**
 * The Mongo query builder.
 *
 * @author Denis Stepanov
 * @since 3.3
 */
@Internal
public final class MongoQueryBuilder implements QueryBuilder {

    /**
     * An object with this property is replaced with an actual query parameter at the runtime.
     */
    public static final String QUERY_PARAMETER_PLACEHOLDER = "$mn_qp";
    public static final String MONGO_DATE_IDENTIFIER = "$date";
    public static final String MONGO_ID_FIELD = "_id";
    private static final String REGEX = "$regex";
    private static final String NOT = "$not";
    private static final String OPTIONS = "$options";

    private final Map<Class, CriterionHandler> queryHandlers = new HashMap<>(30);

    {
        addCriterionHandler(QueryModel.Negation.class, (ctx, obj, negation) -> {
            if (negation.getCriteria().size() == 1) {
                QueryModel.Criterion criterion = negation.getCriteria().iterator().next();
                if (criterion instanceof QueryModel.In in) {
                    handleCriterion(ctx, obj, new QueryModel.NotIn(in.getName(), in.getValue()));
                    return;
                }
                if (criterion instanceof QueryModel.NotIn notIn) {
                    handleCriterion(ctx, obj, new QueryModel.In(notIn.getName(), notIn.getValue()));
                    return;
                }
                if (criterion instanceof QueryModel.PropertyCriterion || criterion instanceof QueryModel.PropertyComparisonCriterion) {
                    Map<String, Object> neg = new LinkedHashMap<>();
                    handleCriterion(ctx, neg, criterion);
                    if (neg.size() != 1) {
                        throw new IllegalStateException("Expected size of 1");
                    }
                    String key = neg.keySet().iterator().next();
                    obj.put(key, singletonMap("$not", neg.get(key)));
                } else {
                    throw new IllegalStateException("Negation is not supported for this criterion: " + criterion);
                }
            } else {
                throw new IllegalStateException("Negation not supported on multiple criterion: " + negation);
            }
        });

        addCriterionHandler(QueryModel.Conjunction.class, (ctx, sb, conjunction) -> handleJunction(ctx, sb, conjunction, "$and"));
        addCriterionHandler(QueryModel.Disjunction.class, (ctx, sb, disjunction) -> handleJunction(ctx, sb, disjunction, "$or"));
        addCriterionHandler(QueryModel.IsTrue.class, (context, sb, criterion) -> handleCriterion(context, sb, new QueryModel.Equals(criterion.getProperty(), true)));
        addCriterionHandler(QueryModel.IsFalse.class, (context, sb, criterion) -> handleCriterion(context, sb, new QueryModel.Equals(criterion.getProperty(), false)));
        addCriterionHandler(QueryModel.IdEquals.class, (context, sb, criterion) -> handleCriterion(context, sb, new QueryModel.Equals("id", criterion.getValue())));
        addCriterionHandler(QueryModel.VersionEquals.class, (context, sb, criterion) -> {
            handleCriterion(context, sb, new QueryModel.Equals(context.getPersistentEntity().getVersion().getName(), criterion.getValue()));
        });
        addCriterionHandler(QueryModel.GreaterThan.class, propertyOperatorExpression("$gt"));
        addCriterionHandler(QueryModel.GreaterThanEquals.class, propertyOperatorExpression("$gte"));
        addCriterionHandler(QueryModel.LessThan.class, propertyOperatorExpression("$lt"));
        addCriterionHandler(QueryModel.LessThanEquals.class, propertyOperatorExpression("$lte"));
        addCriterionHandler(QueryModel.IsNull.class, (context, sb, criterion) -> handleCriterion(context, sb, new QueryModel.Equals(criterion.getProperty(), null)));
        addCriterionHandler(QueryModel.IsNotNull.class, (context, sb, criterion) -> handleCriterion(context, sb, new QueryModel.NotEquals(criterion.getProperty(), null)));
        addCriterionHandler(QueryModel.IsNotNull.class, (context, sb, criterion) -> handleCriterion(context, sb, new QueryModel.NotEquals(criterion.getProperty(), null)));
        addCriterionHandler(QueryModel.GreaterThanProperty.class, comparison("$gt"));
        addCriterionHandler(QueryModel.GreaterThanEqualsProperty.class, comparison("$gte"));
        addCriterionHandler(QueryModel.LessThanProperty.class, comparison("$lt"));
        addCriterionHandler(QueryModel.LessThanEqualsProperty.class, comparison("$lte"));
        addCriterionHandler(QueryModel.EqualsProperty.class, comparison("$eq"));
        addCriterionHandler(QueryModel.NotEqualsProperty.class, comparison("$ne"));
        addCriterionHandler(QueryModel.Between.class, (context, obj, criterion) -> {
            QueryModel.Conjunction conjunction = new QueryModel.Conjunction();
            conjunction.add(new QueryModel.GreaterThanEquals(criterion.getProperty(), criterion.getFrom()));
            conjunction.add(new QueryModel.LessThanEquals(criterion.getProperty(), criterion.getTo()));
            handleCriterion(context, obj, conjunction);
        });
        addCriterionHandler(QueryModel.Regex.class, propertyOperatorExpression("$regex", value -> {
            if (value instanceof BindingParameter) {
                return value;
            }
            return new RegexPattern(value.toString());
        }));
        addCriterionHandler(QueryModel.IsEmpty.class, (context, obj, criterion) -> {
            String criterionPropertyName = getPropertyPersistName(context.getRequiredProperty(criterion).getProperty());
            obj.put("$or", asList(
                    singletonMap(criterionPropertyName, singletonMap("$eq", "")),
                    singletonMap(criterionPropertyName, singletonMap("$exists", false))
            ));
        });
        addCriterionHandler(QueryModel.IsNotEmpty.class, (context, obj, criterion) -> {
            String criterionPropertyName = getPropertyPersistName(context.getRequiredProperty(criterion).getProperty());
            obj.put("$and", asList(
                    singletonMap(criterionPropertyName, singletonMap("$ne", "")),
                    singletonMap(criterionPropertyName, singletonMap("$exists", true))
            ));
        });
        addCriterionHandler(QueryModel.In.class, (context, obj, criterion) -> {
            PersistentPropertyPath propertyPath = context.getRequiredProperty(criterion);
            Object value = criterion.getValue();
            String criterionPropertyName = getPropertyPersistName(context.getRequiredProperty(criterion).getProperty());
            if (value instanceof Iterable) {
                List<?> values = CollectionUtils.iterableToList((Iterable) value);
                obj.put(criterionPropertyName, singletonMap("$in", values.stream().map(val -> valueRepresentation(context, propertyPath, val)).collect(Collectors.toList())));
            } else {
                obj.put(criterionPropertyName, singletonMap("$in", singletonList(valueRepresentation(context, propertyPath, value))));
            }
        });
        addCriterionHandler(QueryModel.NotIn.class, (context, obj, criterion) -> {
            PersistentPropertyPath propertyPath = context.getRequiredProperty(criterion);
            Object value = criterion.getValue();
            String criterionPropertyName = getPropertyPersistName(context.getRequiredProperty(criterion).getProperty());
            if (value instanceof Iterable) {
                List<?> values = CollectionUtils.iterableToList((Iterable) value);
                obj.put(criterionPropertyName, singletonMap("$nin", values.stream().map(val -> valueRepresentation(context, propertyPath, val)).collect(Collectors.toList())));
            } else {
                obj.put(criterionPropertyName, singletonMap("$nin", singletonList(valueRepresentation(context, propertyPath, value))));
            }
        });
        addCriterionHandler(QueryModel.Equals.class, (context, obj, criterion) -> {
            if (criterion.isIgnoreCase()) {
                handleRegexPropertyExpression(context, obj, criterion, true, false, true, true);
                return;
            }
            handlePropertyOperatorExpression(context, obj, criterion, "$eq", null);
        });
        addCriterionHandler(QueryModel.NotEquals.class, (context, obj, criterion) -> {
            if (criterion.isIgnoreCase()) {
                handleRegexPropertyExpression(context, obj, criterion, true, true, true, true);
                return;
            }
            handlePropertyOperatorExpression(context, obj, criterion, "$ne", null);
        });
        addCriterionHandler(QueryModel.StartsWith.class, (context, obj, criterion) -> {
            handleRegexPropertyExpression(context, obj, criterion, criterion.isIgnoreCase(), false, true, false);
        });
        addCriterionHandler(QueryModel.EndsWith.class, (context, obj, criterion) -> {
            handleRegexPropertyExpression(context, obj, criterion, criterion.isIgnoreCase(), false, false, true);
        });
        addCriterionHandler(QueryModel.Contains.class, (context, obj, criterion) -> {
            handleRegexPropertyExpression(context, obj, criterion, criterion.isIgnoreCase(), false, false, false);
        });
    }

    private <T extends QueryModel.PropertyCriterion> CriterionHandler<T> propertyOperatorExpression(String op) {
        return propertyOperatorExpression(op, null);
    }

    private <T extends QueryModel.PropertyCriterion> CriterionHandler<T> propertyOperatorExpression(String op, Function<Object, Object> mapper) {
        return (context, obj, criterion) -> handlePropertyOperatorExpression(context, obj, criterion, op, mapper);
    }

    private <T extends QueryModel.PropertyCriterion> void handlePropertyOperatorExpression(CriteriaContext context, Map<String, Object> obj,
                                                                                           T criterion, String op, Function<Object, Object> mapper) {
        Object value = criterion.getValue();
        if (mapper != null) {
            value = mapper.apply(value);
        }
        PersistentPropertyPath propertyPath = context.getRequiredProperty(criterion);
        Object finalValue = value;
        traversePersistentProperties(propertyPath.getAssociations(), propertyPath.getProperty(), (associations, property) -> {
            String path = asPath(associations, property);
            obj.put(path, singletonMap(op, valueRepresentation(context, propertyPath, PersistentPropertyPath.of(associations, property), finalValue)));
        });
    }

    /**
     * Handles criteria by string value using $regex in MongoDB. It supports case ignore, negation, whole world matching, starts with, contains and ends with.
     *
     * @param context the criterion context
     * @param obj the object to populate with criteria
     * @param criterion the criterion
     * @param ignoreCase whether to regex search using ignore case
     * @param negate used with not equal, to negate regex search
     * @param startsWith whether to search regex starting with
     * @param endsWith whether to search regex ending with
     * @param <T> the criterion type
     */
    private <T extends QueryModel.PropertyCriterion> void handleRegexPropertyExpression(CriteriaContext context, Map<String, Object> obj, T criterion,
                                                                                        boolean ignoreCase, boolean negate, boolean startsWith, boolean endsWith) {
        PersistentPropertyPath propertyPath = context.getRequiredProperty(criterion);
        Object value = criterion.getValue();
        Object filterValue;
        Map<String, Object> regexCriteria = new HashMap<>(2);
        regexCriteria.put(OPTIONS, ignoreCase ? "i" : "");
        String regexValue;
        if (value instanceof BindingParameter) {
            int index = context.pushParameter(
                (BindingParameter) value,
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
            filterValue = singletonMap(NOT, regexCriteria);
        } else {
            filterValue = regexCriteria;
        }
        String criterionPropertyName = getPropertyPersistName(propertyPath.getProperty());
        obj.put(criterionPropertyName, filterValue);
    }

    private String getPropertyPersistName(PersistentProperty property) {
        if (property.getOwner() != null && property.getOwner().getIdentity() == property) {
            return MONGO_ID_FIELD;
        }
        return property.getAnnotationMetadata()
                .stringValue(SerdeConfig.class, SerdeConfig.PROPERTY)
                .orElseGet(property::getName);
    }

    private Object valueRepresentation(CriteriaContext context, PersistentPropertyPath propertyPath, Object value) {
        return valueRepresentation(context, propertyPath, propertyPath, value);
    }

    private Object valueRepresentation(CriteriaContext context,
                                       PersistentPropertyPath inPropertyPath,
                                       PersistentPropertyPath outPropertyPath,
                                       Object value) {
        if (value instanceof LocalDate) {
            return singletonMap(MONGO_DATE_IDENTIFIER, formatDate((LocalDate) value));
        }
        if (value instanceof LocalDateTime) {
            return singletonMap(MONGO_DATE_IDENTIFIER, formatDate((LocalDateTime) value));
        }
        if (value instanceof BindingParameter) {
            int index = context.pushParameter(
                    (BindingParameter) value,
                    newBindingContext(inPropertyPath, outPropertyPath)
            );
            return singletonMap(QUERY_PARAMETER_PLACEHOLDER, index);
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

    private <T extends QueryModel.PropertyComparisonCriterion> CriterionHandler<T> comparison(String operator) {
        return (ctx, obj, comparisonCriterion) -> {
            PersistentPropertyPath p1 = ctx.getRequiredProperty(comparisonCriterion.getProperty(), comparisonCriterion.getClass());
            PersistentPropertyPath p2 = ctx.getRequiredProperty(comparisonCriterion.getOtherProperty(), comparisonCriterion.getClass());
            obj.put("$expr", singletonMap(
                    operator, asList(
                            "$" + p1.getPath(), "$" + p2.getPath()
                    )
            ));
        };
    }

    private Object asLiteral(@Nullable Object value) {
        if (value instanceof RegexPattern) {
            return "'" + Pattern.quote(((RegexPattern) value).value) + "'";
        }
        return value;
    }

    @Override
    public QueryResult buildInsert(AnnotationMetadata repositoryMetadata, PersistentEntity entity) {
        return null;
    }

    @Override
    public QueryResult buildQuery(AnnotationMetadata annotationMetadata, QueryModel query) {
        ArgumentUtils.requireNonNull("annotationMetadata", annotationMetadata);
        ArgumentUtils.requireNonNull("query", query);

        QueryState queryState = new QueryState(query, true);

        Map<String, Object> predicateObj = new LinkedHashMap<>();
        Map<String, Object> group = new LinkedHashMap<>();
        Map<String, Object> projectionObj = new LinkedHashMap<>();
        Map<String, Object> countObj = new LinkedHashMap<>();

        addLookups(query.getJoinPaths(), queryState);
        List<Map<String, Object>> pipeline = queryState.rootLookups.pipeline;
        buildProjection(query.getProjections(), query.getPersistentEntity(), group, projectionObj, countObj);
        QueryModel.Junction criteria = query.getCriteria();
        if (!criteria.isEmpty()) {
            predicateObj = buildWhereClause(annotationMetadata, criteria, queryState);
        }

        if (!predicateObj.isEmpty()) {
            pipeline.add(singletonMap("$match", predicateObj));
        }
        if (!group.isEmpty()) {
            group.put(MONGO_ID_FIELD, null);
            pipeline.add(singletonMap("$group", group));
        }
        if (!countObj.isEmpty()) {
            pipeline.add(countObj);
        }
        if (!projectionObj.isEmpty()) {
            pipeline.add(singletonMap("$project", projectionObj));
        } else {
            String customProjection = annotationMetadata.stringValue(MongoAnnotations.PROJECTION).orElse(null);
            if (customProjection != null) {
                pipeline.add(singletonMap("$project", new RawJsonValue(customProjection)));
            }
        }
        Sort sort = query.getSort();
        if (sort.isSorted() && !sort.getOrderBy().isEmpty()) {
            Map<String, Object> sortObj = new LinkedHashMap<>();
            sort.getOrderBy().forEach(order -> sortObj.put(order.getProperty(), order.isAscending() ? 1 : -1));
            pipeline.add(singletonMap("$sort", sortObj));
        } else {
            String customSort = annotationMetadata.stringValue(MongoAnnotations.SORT).orElse(null);
            if (customSort != null) {
                pipeline.add(singletonMap("$sort", new RawJsonValue(customSort)));
            }
        }
        if (query.getOffset() > 0) {
            pipeline.add(singletonMap("$skip", query.getOffset()));
        }
        if (query.getMax() != -1) {
            pipeline.add(singletonMap("$limit", query.getMax()));
        }

        String q;
        if (pipeline.isEmpty()) {
            q = "{}";
        } else if (isMatchOnlyStage(pipeline)) {
            q = toJsonString(predicateObj);
        } else {
            q = toJsonString(pipeline);
        }
        return new QueryResult() {

            @NonNull
            @Override
            public String getQuery() {
                return q;
            }

            @Override
            public int getMax() {
                return query.getMax();
            }

            @Override
            public long getOffset() {
                return query.getOffset();
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

    private void addLookups(Collection<JoinPath> joins, QueryState queryState) {
        if (joins.isEmpty()) {
            return;
        }
        List<String> joined = joins.stream().map(JoinPath::getPath)
                .sorted((o1, o2) -> Comparator.comparingInt(String::length).thenComparing(String::compareTo).compare(o1, o2))
                .collect(Collectors.toList());
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
                            singletonMap("$replaceRoot", singletonMap("newRoot", "$" + joinedCollectionName))
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
                        if (!(mappedByPath.getProperty() instanceof Association)) {
                            throw new IllegalStateException("Expected association as a mapped path: " + mappedBy);
                        }

                        List<String> localMatchFields = new ArrayList<>();
                        List<String> foreignMatchFields = new ArrayList<>();
                        traversePersistentProperties(currentLookup.persistentEntity.getIdentity(), (associations, p) -> {
                            String fieldPath = asPath(associations, p);
                            localMatchFields.add(fieldPath);
                        });

                        List<Association> mappedAssociations = new ArrayList<>(mappedByPath.getAssociations());
                        mappedAssociations.add((Association) mappedByPath.getProperty());

                        traversePersistentProperties(mappedAssociations, currentLookup.persistentEntity.getIdentity(), (associations, p) -> {
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
                        List<Association> mappedAssociations = new ArrayList<>(propertyPath.getAssociations());
                        mappedAssociations.add((Association) propertyPath.getProperty());

                        List<String> localMatchFields = new ArrayList<>();
                        List<String> foreignMatchFields = new ArrayList<>();
                        PersistentProperty identity = lookupStage.persistentEntity.getIdentity();
                        if (identity == null) {
                            throw new IllegalStateException("Null identity of persistent entity: " + lookupStage.persistentEntity);
                        }
                        traversePersistentProperties(mappedAssociations, identity, (associations, p) -> {
                            String fieldPath = asPath(associations, p);
                            localMatchFields.add(fieldPath);
                        });
                        traversePersistentProperties(identity, (associations, p) -> {
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
        List<String> fields = new ArrayList<>();
        traversePersistentProperties(entity.getIdentity(), (associations, property) -> {
            fields.add(asPath(associations, property));
        });
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
        List<String> fields = new ArrayList<>();
        traversePersistentProperties(identity, (associations, property) -> {
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
        StringJoiner joiner = new StringJoiner(".");
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
        return singletonMap("$lookup", lookup);
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
            matches.add(singletonMap("$eq", Arrays.asList("$$" + var, "$" + foreignIt.next())));
        }

        Map<String, Object> match;
        if (matches.size() > 1) {
            match = singletonMap("$match", singletonMap("$expr", singletonMap("$and", matches)));
        } else {
            match = singletonMap("$match", singletonMap("$expr", matches.iterator().next()));
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
        return singletonMap("$lookup", lookup);
    }

    private Map<String, Object> unwind(String path, boolean preserveNullAndEmptyArrays) {
        Map<String, Object> unwind = new LinkedHashMap<>();
        unwind.put("path", path);
        unwind.put("preserveNullAndEmptyArrays", preserveNullAndEmptyArrays);
        return singletonMap("$unwind", unwind);
    }

    private boolean isMatchOnlyStage(List<Map<String, Object>> pipeline) {
        return pipeline.size() == 1 && pipeline.iterator().next().containsKey("$match");
    }

    private Map<String, Object> buildWhereClause(AnnotationMetadata annotationMetadata, QueryModel.Junction criteria, QueryState queryState) {
        CriteriaContext ctx = new CriteriaContext() {

            @Override
            public QueryState getQueryState() {
                return queryState;
            }

            @Override
            public PersistentEntity getPersistentEntity() {
                return queryState.getEntity();
            }

            @Override
            public PersistentPropertyPath getRequiredProperty(String name, Class<?> criterionClazz) {
                return findProperty(queryState, name, criterionClazz);
            }

        };

        Map<String, Object> obj = new LinkedHashMap<>();
        handleCriterion(ctx, obj, criteria);
        return obj;
    }

    private void buildProjection(List<QueryModel.Projection> projectionList,
                                 PersistentEntity entity,
                                 Map<String, Object> groupObj,
                                 Map<String, Object> projectionObj,
                                 Map<String, Object> countObj) {
        if (!projectionList.isEmpty()) {
            for (QueryModel.Projection projection : projectionList) {
                if (projection instanceof QueryModel.LiteralProjection literalProjection) {
                    projectionObj.put("val", singletonMap("$literal", asLiteral(literalProjection.getValue())));
                } else if (projection instanceof QueryModel.CountProjection) {
                    countObj.put("$count", "result");
                } else if (projection instanceof QueryModel.DistinctProjection) {
                    throw new UnsupportedOperationException("Not implemented yet");
                } else if (projection instanceof QueryModel.IdProjection) {
                    projectionObj.put(MONGO_ID_FIELD, 1);
                } else if (projection instanceof QueryModel.PropertyProjection pp) {
                    if (projection instanceof QueryModel.AvgProjection) {
                        addProjection(groupObj, pp, "$avg");
                    } else if (projection instanceof QueryModel.DistinctPropertyProjection) {
                        throw new UnsupportedOperationException("Not implemented yet");
                    } else if (projection instanceof QueryModel.SumProjection) {
                        addProjection(groupObj, pp, "$sum");
                    } else if (projection instanceof QueryModel.MinProjection) {
                        addProjection(groupObj, pp, "$min");
                    } else if (projection instanceof QueryModel.MaxProjection) {
                        addProjection(groupObj, pp, "$max");
                    } else if (projection instanceof QueryModel.CountDistinctProjection) {
                        throw new UnsupportedOperationException("Not implemented yet");
                    } else {
                        String propertyName = pp.getPropertyName();
                        PersistentPropertyPath propertyPath = entity.getPropertyPath(propertyName);
                        if (propertyPath == null) {
                            throw new IllegalArgumentException("Cannot project on non-existent property: " + propertyName);
                        }
                        projectionObj.put(getPropertyPersistName(propertyPath.getProperty()), 1);
                    }
                }
            }
        }
    }

    private void addProjection(Map<String, Object> groupBy, QueryModel.PropertyProjection pr, String op) {
        groupBy.put(pr.getAlias().orElse(pr.getPropertyName()), singletonMap(op, "$" + pr.getPropertyName()));
    }

    @NonNull
    private PersistentPropertyPath findProperty(QueryState queryState, String name, Class criterionType) {
        return findPropertyInternal(queryState, queryState.getEntity(), name, criterionType);
    }

    private PersistentPropertyPath findPropertyInternal(QueryState queryState, PersistentEntity entity, String name, Class criterionType) {
        PersistentPropertyPath propertyPath = entity.getPropertyPath(name);
        if (propertyPath != null) {
            if (propertyPath.getAssociations().isEmpty()) {
                return propertyPath;
            }
            Association joinAssociation = null;
            StringJoiner joinPathJoiner = new StringJoiner(".");
            for (Association association : propertyPath.getAssociations()) {
                joinPathJoiner.add(association.getName());
                if (association instanceof Embedded) {
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
//                    lastJoinAlias = joinInPath(queryState, joinStringPath);
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
            if (criterionType == null || criterionType == Sort.Order.class) {
                throw new IllegalArgumentException("Cannot order on non-existent property path: " + name);
            } else {
                throw new IllegalArgumentException("Cannot use [" + criterionType.getSimpleName() + "] criterion on non-existent property path: " + name);
            }
        }
        return propertyPath;
    }

    private void handleJunction(CriteriaContext ctx, Map<String, Object> query, QueryModel.Junction criteria, String operator) {
        if (criteria.getCriteria().size() == 1) {
            handleCriterion(ctx, query, criteria.getCriteria().iterator().next());
        } else {
            List<Object> ops = new ArrayList<>(criteria.getCriteria().size());
            query.put(operator, ops);
            for (QueryModel.Criterion criterion : criteria.getCriteria()) {
                Map<String, Object> criterionObj = new LinkedHashMap<>();
                ops.add(criterionObj);
                handleCriterion(ctx, criterionObj, criterion);
            }
        }
    }

    private void handleCriterion(CriteriaContext ctx, Map<String, Object> query, QueryModel.Criterion criterion) {
        CriterionHandler<QueryModel.Criterion> criterionHandler = queryHandlers.get(criterion.getClass());
        if (criterionHandler == null) {
            throw new IllegalArgumentException("Queries of type " + criterion.getClass().getSimpleName() + " are not supported by this implementation");
        }
        criterionHandler.handle(ctx, query, criterion);
    }

    @Override
    public QueryResult buildUpdate(AnnotationMetadata annotationMetadata, QueryModel query, List<String> propertiesToUpdate) {
        throw new IllegalStateException("Only 'buildUpdate' with 'Map<String, Object> propertiesToUpdate' is supported");
    }

    @Override
    public QueryResult buildUpdate(AnnotationMetadata annotationMetadata, QueryModel query, Map<String, Object> propertiesToUpdate) {
        ArgumentUtils.requireNonNull("annotationMetadata", annotationMetadata);
        ArgumentUtils.requireNonNull("query", query);
        ArgumentUtils.requireNonNull("propertiesToUpdate", propertiesToUpdate);

        QueryState queryState = new QueryState(query, true);

        QueryModel.Junction criteria = query.getCriteria();

        String predicateQuery = "";
        if (!criteria.isEmpty()) {
            Map<String, Object> predicate = buildWhereClause(annotationMetadata, criteria, queryState);
            predicateQuery = toJsonString(predicate);
        }

        Map<String, Object> sets = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : propertiesToUpdate.entrySet()) {
            if (e.getValue() instanceof BindingParameter) {
                PersistentPropertyPath propertyPath = findProperty(queryState, e.getKey(), null);
                int index = queryState.pushParameter(
                        (BindingParameter) e.getValue(),
                        newBindingContext(propertyPath)
                );
                sets.put(e.getKey(), singletonMap(QUERY_PARAMETER_PLACEHOLDER, index));
            } else {
                sets.put(e.getKey(), e.getValue());
            }
        }

        String update = toJsonString(singletonMap("$set", sets));

        String finalPredicateQuery = predicateQuery;
        return new QueryResult() {

            @NonNull
            @Override
            public String getQuery() {
                return finalPredicateQuery;
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
    public QueryResult buildDelete(AnnotationMetadata annotationMetadata, QueryModel query) {
        ArgumentUtils.requireNonNull("annotationMetadata", annotationMetadata);
        ArgumentUtils.requireNonNull("query", query);

        QueryState queryState = new QueryState(query, true);

        QueryModel.Junction criteria = query.getCriteria();

        String predicateQuery = "";
        if (!criteria.isEmpty()) {
            Map<String, Object> predicate = buildWhereClause(annotationMetadata, criteria, queryState);
            predicateQuery = toJsonString(predicate);
        }

        return QueryResult.of(
                predicateQuery,
                Collections.emptyList(),
                queryState.getParameterBindings(),
                queryState.getAdditionalRequiredParameters(),
                query.getMax(),
                query.getOffset()
        );
    }

    @Override
    public QueryResult buildOrderBy(PersistentEntity entity, Sort sort) {
        throw new UnsupportedOperationException();
    }

    @Override
    public QueryResult buildPagination(Pageable pageable) {
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
        if (obj instanceof Map) {
            return ((Map<?, ?>) obj).isEmpty();
        }
        if (obj instanceof Collection) {
            return ((Collection<?>) obj).isEmpty();
        }
        return false;
    }

    private void appendArray(StringBuilder sb, Collection<Object> collection) {
        sb.append("[");
        for (Iterator<Object> iterator = collection.iterator(); iterator.hasNext(); ) {
            Object value = iterator.next();
            append(sb, value);
            if (iterator.hasNext()) {
                sb.append(",");
            }
        }
        sb.append("]");
    }

    private void append(StringBuilder sb, Object obj) {
        if (obj instanceof Map) {
            appendMap(sb, (Map) obj);
        } else if (obj instanceof Collection) {
            appendArray(sb, (Collection<Object>) obj);
        } else if (obj instanceof RawJsonValue) {
            sb.append(((RawJsonValue) obj).value);
        } else if (obj == null) {
            sb.append("null");
        } else if (obj instanceof Boolean) {
            sb.append(obj.toString().toLowerCase(Locale.ROOT));
        } else if (obj instanceof Number) {
            sb.append(obj);
        } else {
            sb.append("'");
            sb.append(obj);
            sb.append("'");
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

    /**
     * Adds criterion handler.
     *
     * @param clazz   The handler class
     * @param handler The handler
     * @param <T>     The criterion type
     */
    private <T extends QueryModel.Criterion> void addCriterionHandler(Class<T> clazz, CriterionHandler<T> handler) {
        queryHandlers.put(clazz, handler);
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
     * Traverses properties that should be persisted.
     *
     * @param property The property to start traversing from
     * @param consumer The function to invoke on every property
     */
    private void traversePersistentProperties(PersistentProperty property, BiConsumer<List<Association>, PersistentProperty> consumer) {
        traversePersistentProperties(Collections.emptyList(), property, consumer);
    }

    private void traversePersistentProperties(List<Association> associations,
                                              PersistentProperty property,
                                              BiConsumer<List<Association>, PersistentProperty> consumerProperty) {
        PersistentEntityUtils.traversePersistentProperties(associations, property, consumerProperty);
    }

    private static final class RawJsonValue {

        private final String value;

        private RawJsonValue(String value) {
            this.value = value;
        }
    }

    /**
     * A criterion handler.
     *
     * @param <T> The criterion type
     */
    private interface CriterionHandler<T extends QueryModel.Criterion> {

        /**
         * Handles a criterion.
         *
         * @param context   The context
         * @param query     The query
         * @param criterion The criterion
         */
        void handle(CriteriaContext context, Map<String, Object> query, T criterion);
    }

    /**
     * A criterion context.
     */
    private interface CriteriaContext extends PropertyParameterCreator {

        QueryState getQueryState();

        PersistentEntity getPersistentEntity();

        PersistentPropertyPath getRequiredProperty(String name, Class<?> criterionClazz);

        default int pushParameter(@NotNull BindingParameter bindingParameter, @NotNull BindingParameter.BindingContext bindingContext) {
            return getQueryState().pushParameter(bindingParameter, bindingContext);
        }

        default PersistentPropertyPath getRequiredProperty(QueryModel.PropertyNameCriterion propertyCriterion) {
            return getRequiredProperty(propertyCriterion.getProperty(), propertyCriterion.getClass());
        }

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
    protected final class QueryState implements PropertyParameterCreator {
        private final Set<String> joinPaths = new TreeSet<>();
        private final AtomicInteger position = new AtomicInteger(0);
        private final Map<String, String> additionalRequiredParameters = new LinkedHashMap<>();
        private final List<QueryParameterBinding> parameterBindings;
        private final boolean allowJoins;
        private final PersistentEntity entity;

        private final LookupsStage rootLookups;

        private QueryState(QueryModel query, boolean allowJoins) {
            this.allowJoins = allowJoins;
            this.entity = query.getPersistentEntity();
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
        public @NotNull Map<String, String> getAdditionalRequiredParameters() {
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
        public int pushParameter(@NotNull BindingParameter bindingParameter, @NotNull BindingParameter.BindingContext bindingContext) {
            int index = position.getAndIncrement();
            bindingContext = bindingContext.index(index);
            parameterBindings.add(
                    bindingParameter.bind(bindingContext)
            );
            return index;
        }
    }

    private interface PropertyParameterCreator {

        int pushParameter(@NotNull BindingParameter bindingParameter,
                          @NotNull BindingParameter.BindingContext bindingContext);

    }

    private static final class RegexPattern {
        private final String value;

        private RegexPattern(String value) {
            this.value = value;
        }
    }
}
