/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.nitrite.model.query.builder;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentEntityUtils;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.IExpression;
import io.micronaut.data.model.jpa.criteria.IPredicate;
import io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils;
import io.micronaut.data.model.jpa.criteria.impl.expression.BinaryExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.UnaryExpression;
import io.micronaut.data.model.jpa.criteria.impl.predicate.ConjunctionPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.DisjunctionPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.ExistsSubqueryPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.LikePredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.NegatedPredicate;
import io.micronaut.data.model.query.BindingParameter;
import io.micronaut.data.model.query.impl.AdvancedPredicateVisitor;
import jakarta.persistence.criteria.Expression;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Translates JPA Criteria predicates into a NitriteDB JSON filter map. */
public final class NitritePredicateVisitor implements AdvancedPredicateVisitor<PersistentPropertyPath> {

    static final String ID_FIELD = "id";
    private static final String REGEX = "$regex";
    private static final String NOT = "$not";

    private final PersistentEntity persistentEntity;
    private final NitriteQueryState queryState;
    private final NitriteExpressionHandler expressionHandler;
    private Map<String, Object> query;

    NitritePredicateVisitor(final NitriteQueryState queryState, final Map<String, Object> query, final NitriteExpressionHandler expressionHandler) {
        this.queryState = queryState;
        this.query = query;
        this.persistentEntity = queryState.getEntity();
        this.expressionHandler = expressionHandler;
    }

    // -------------------------------------------------------------------------
    // AdvancedPredicateVisitor implementation
    // -------------------------------------------------------------------------

    @Override
    public PersistentPropertyPath getRequiredProperty(
        final io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> persistentPropertyPath) {
        return persistentPropertyPath.getPropertyPath();
    }

    @Override
    public void visitIdEquals(final Expression<?> expression) {
        if (persistentEntity.hasCompositeIdentity()) {
            throw new IllegalStateException("Composite ID not supported!");
        } else if (persistentEntity.hasIdentity()) {
            query.put(
                ID_FIELD,
                valueRepresentation(
                    queryState, new PersistentPropertyPath(persistentEntity.getIdentity()), expression));
        } else {
            throw new IllegalStateException("No ID found for entity: " + persistentEntity.getName());
        }
    }

    @Override
    public void visitEquals(
        final Expression<?> leftExpression,
        final Expression<?> rightExpression,
        final boolean ignoreCase) {
        if (ignoreCase) {
            handleRegexExpression(leftExpression, true, false, false, false, rightExpression, false);
        } else {
            appendOperatorExpression(leftExpression, "$eq", rightExpression);
        }
    }

    @Override
    public void visitNotEquals(
        final Expression<?> leftExpression,
        final Expression<?> rightExpression,
        final boolean ignoreCase) {
        if (ignoreCase) {
            handleRegexExpression(leftExpression, true, true, false, false, rightExpression, false);
        } else {
            appendOperatorExpression(leftExpression, "$ne", rightExpression);
        }
    }

    @Override
    public void visitGreaterThan(
        final Expression<?> leftExpression, final Expression<?> rightExpression) {
        appendOperatorExpression(leftExpression, "$gt", rightExpression);
    }

    @Override
    public void visitGreaterThanOrEquals(
        final Expression<?> leftExpression, final Expression<?> rightExpression) {
        appendOperatorExpression(leftExpression, "$gte", rightExpression);
    }

    @Override
    public void visitLessThan(
        final Expression<?> leftExpression, final Expression<?> rightExpression) {
        appendOperatorExpression(leftExpression, "$lt", rightExpression);
    }

    @Override
    public void visitLessThanOrEquals(
        final Expression<?> leftExpression, final Expression<?> rightExpression) {
        appendOperatorExpression(leftExpression, "$lte", rightExpression);
    }

    @Override
    public void visitIsNull(final Expression<?> expression) {
        PersistentPropertyPath propertyPath =
            CriteriaUtils.requireProperty(expression).getPropertyPath();
        PersistentEntityUtils.traversePersistentProperties(
            propertyPath,
            (associations, property) -> {
                String path = getFieldNameForNullCheck(associations, property);
                query.put(path, Collections.singletonMap("$eq", null));
            });
    }

    @Override
    public void visitIsNotNull(final Expression<?> expression) {
        PersistentPropertyPath propertyPath =
            CriteriaUtils.requireProperty(expression).getPropertyPath();
        PersistentEntityUtils.traversePersistentProperties(
            propertyPath,
            (associations, property) -> {
                String path = getFieldNameForNullCheck(associations, property);
                query.put(path, Collections.singletonMap("$ne", null)); // notEq(null)
            });
    }

    /**
     * Get the field name for null checks on associations.
     * For non-embedded associations, returns just the association's persisted name (e.g., "author_id").
     * For embedded associations or regular properties, returns the full path.
     */
    private static String getFieldNameForNullCheck(
        final Collection<Association> associations, final PersistentProperty property) {
        if (associations.isEmpty()) {
            return property.getPersistedName();
        }
        // For non-embedded associations, just use the last association's persisted name
        // because Nitrite stores the reference as a single field (e.g., "author_id")
        Association lastAssoc = null;
        for (Association assoc : associations) {
            if (!assoc.isEmbedded()) {
                lastAssoc = assoc;
            }
        }
        if (lastAssoc != null) {
            return lastAssoc.getPersistedName();
        }
        // For embedded associations, build the full path
        StringBuilder sb = new StringBuilder();
        for (Association association : associations) {
            sb.append(association.getPersistedName()).append(".");
        }
        sb.append(property.getPersistedName());
        return sb.toString();
    }

    @Override
    public void visitIsTrue(final Expression<?> expression) {
        PersistentPropertyPath propertyPath =
            CriteriaUtils.requireProperty(expression).getPropertyPath();
        PersistentEntityUtils.traversePersistentProperties(
            propertyPath,
            (associations, property) -> {
                String path = asPath(associations, property);
                query.put(path, Collections.singletonMap("$eq", Boolean.TRUE));
            });
    }

    @Override
    public void visitIsFalse(final Expression<?> expression) {
        PersistentPropertyPath propertyPath =
            CriteriaUtils.requireProperty(expression).getPropertyPath();
        PersistentEntityUtils.traversePersistentProperties(
            propertyPath,
            (associations, property) -> {
                String path = asPath(associations, property);
                query.put(path, Collections.singletonMap("$eq", Boolean.FALSE));
            });
    }

    @Override
    public void visitIsEmpty(final Expression<?> expression) {
        PersistentPropertyPath propertyPath =
            CriteriaUtils.requireProperty(expression).getPropertyPath();
        PersistentEntityUtils.traversePersistentProperties(
            propertyPath,
            (associations, property) -> {
                String path = asPath(associations, property);
                query.put("$or", List.of(
                    Collections.singletonMap(path, Collections.singletonMap("$eq", "")),
                    Collections.singletonMap(path, Collections.singletonMap("$exists", false))
                ));
            });
    }

    @Override
    public void visitIsNotEmpty(final Expression<?> expression) {
        PersistentPropertyPath propertyPath =
            CriteriaUtils.requireProperty(expression).getPropertyPath();
        PersistentEntityUtils.traversePersistentProperties(
            propertyPath,
            (associations, property) -> {
                String path = asPath(associations, property);
                query.put("$and", List.of(
                    Collections.singletonMap(path, Collections.singletonMap("$ne", "")),
                    Collections.singletonMap(path, Collections.singletonMap("$exists", true))
                ));
            });
    }

    /**
     * Visit IN predicate, handling both literal collections and collection parameters.
     * <p>
     * When the values collection contains a single {@link BindingParameter}, it represents
     * a collection parameter (e.g., {@code WHERE id IN :ids}). The placeholder is stored
     * and resolved at runtime when the actual collection values are available.
     * <p>
     * If values is null or empty, a filter that matches nothing is added (since IN with no
     * values should match no documents).
     *
     * @param expression the property expression
     * @param values the collection of values (or single BindingParameter for collection params)
     * @param negated whether this is a NOT IN operation
     */
    @Override
    public void visitIn(
        final Expression<?> expression, final Collection<?> values, final boolean negated) {
        PersistentPropertyPath propertyPath =
            CriteriaUtils.requireProperty(expression).getPropertyPath();
        String fieldName = getFieldName(propertyPath);

        // Handle null or empty collection - IN with no values matches nothing, NOT IN matches all
        if (values == null || values.isEmpty()) {
            if (!negated) {
                // IN with empty set matches nothing - add impossible condition
                query.put("_id", Collections.singletonMap("$eq", null));
            }
            return;
        }

        // Handle case where values is a single BindingParameter representing a collection
        List<Object> resolvedValues;
        if (values.size() == 1) {
            Object singleValue = values.iterator().next();
            if (singleValue instanceof BindingParameter bp) {
                // Bind the collection parameter and use its elements
                int index = queryState.pushParameter(bp, newBindingContext(propertyPath, propertyPath));
                resolvedValues = List.of(NitriteQueryBuilder.QUERY_PARAMETER_PLACEHOLDER + ":" + index);
            } else if (singleValue instanceof Collection<?> nestedColl) {
                // Handle nested collection from Criteria API
                if (nestedColl.isEmpty()) {
                    if (!negated) {
                        query.put("_id", Map.of("$eq", null));
                    }
                    return;
                }
                resolvedValues = nestedColl.stream()
                    .map(val -> valueRepresentation(queryState, propertyPath, val))
                    .toList();
            } else {
                resolvedValues = List.of(valueRepresentation(queryState, propertyPath, singleValue));
            }
        } else {
            resolvedValues = values.stream()
                .map(val -> valueRepresentation(queryState, propertyPath, val))
                .toList();
        }

        // After resolving, check if we ended up with no values (defensive check)
        if (resolvedValues.isEmpty()) {
            if (!negated) {
                query.put("_id", Collections.singletonMap("$eq", null));  // IN with empty set matches nothing
            }
            return;  // NOT IN with empty set matches all
        }

        query.put(
            fieldName,
            Map.of(
                negated ? "$nin" : "$in",
                resolvedValues));
    }

    @Override
    public void visitInBetween(
        final Expression<?> value,
        final Expression<?> from,
        final Expression<?> to,
        final boolean negated) {
        PersistentPropertyPath propertyPath = CriteriaUtils.requireProperty(value).getPropertyPath();
        // Use Nitrite's native $between operator instead of decomposing to $gte + $lte
        List<Object> betweenValues = Arrays.asList(
            valueRepresentation(queryState, propertyPath, propertyPath, from),
            valueRepresentation(queryState, propertyPath, propertyPath, to)
        );
        Map<String, Object> betweenOp = Map.of("$between", betweenValues);
        PersistentEntityUtils.traversePersistentProperties(
            propertyPath,
            (associations, property) -> {
                String path = asPath(associations, property);
                query.put(path, negated ? Map.of(NOT, betweenOp) : betweenOp);
            });
    }

    @Override
    public void visitContains(
        final Expression<?> leftExpression,
        final Expression<?> rightExpression,
        final boolean ignoreCase) {
        handleRegexExpression(leftExpression, ignoreCase, false, false, false, rightExpression, false);
    }

    @Override
    public void visitEndsWith(
        final Expression<?> leftExpression,
        final Expression<?> rightExpression,
        final boolean ignoreCase) {
        handleRegexExpression(leftExpression, ignoreCase, false, false, true, rightExpression, false);
    }

    @Override
    public void visitStartsWith(
        final Expression<?> leftExpression,
        final Expression<?> rightExpression,
        final boolean ignoreCase) {
        handleRegexExpression(leftExpression, ignoreCase, false, true, false, rightExpression, false);
    }

    @Override
    public void visitRegexp(final Expression<?> leftExpression, final Expression<?> rightExpression) {
        PersistentPropertyPath propertyPath =
            CriteriaUtils.requireProperty(leftExpression).getPropertyPath();
        String fieldName = getFieldName(propertyPath);
        Object regexValue = expressionHandler.resolveRegexValue(queryState, propertyPath, rightExpression);
        Map<String, Object> fieldFilter = new LinkedHashMap<>();
        fieldFilter.put(REGEX, regexValue);
        query.put(fieldName, fieldFilter);
    }

    @Override
    public void visitGeoWithin(final Expression<?> leftExpression, final Expression<?> expression) {
        PersistentPropertyPath propertyPath =
            CriteriaUtils.requireProperty(leftExpression).getPropertyPath();
        String fieldName = getFieldName(propertyPath);
        Object geoValue = valueRepresentation(queryState, propertyPath, expression);
        query.put(fieldName, Collections.singletonMap("$within", geoValue));
    }

    @Override
    public void visitGeoIntersects(final Expression<?> leftExpression, final Expression<?> expression) {
        PersistentPropertyPath propertyPath =
            CriteriaUtils.requireProperty(leftExpression).getPropertyPath();
        String fieldName = getFieldName(propertyPath);
        Object geoValue = valueRepresentation(queryState, propertyPath, expression);
        query.put(fieldName, Collections.singletonMap("$intersects", geoValue));
    }

    @Override
    public void visitNear(
        final Expression<?> leftExpression,
        final Expression<?> geometryExpression,
        final Expression<? extends Number> distanceExpression) {
        PersistentPropertyPath propertyPath =
            CriteriaUtils.requireProperty(leftExpression).getPropertyPath();
        String fieldName = getFieldName(propertyPath);
        Object geoValue = valueRepresentation(queryState, propertyPath, geometryExpression);
        Object distValue = valueRepresentation(queryState, propertyPath, propertyPath, distanceExpression);
        Map<String, Object> nearMap = new LinkedHashMap<>();
        nearMap.put("center", geoValue);
        nearMap.put("distance", distValue);
        query.put(fieldName, Collections.singletonMap("$near", nearMap));
    }

    @Override
    public void visitArrayContains(
        final Expression<?> leftExpression, final Expression<?> expression) {
        PersistentPropertyPath propertyPath =
            CriteriaUtils.requireProperty(leftExpression).getPropertyPath();
        String fieldName = getFieldName(propertyPath);
        List<Object> criteriaValues = expressionHandler.resolveCollectionValue(queryState, propertyPath, expression);
        query.put(fieldName, Collections.singletonMap("$all", criteriaValues));
    }

    private void visitSinglePredicate(final Collection<? extends IExpression<Boolean>> predicates) {
        IExpression<Boolean> single = predicates.iterator().next();
        if (single instanceof IPredicate iPredicate) {
            iPredicate.visitPredicate(this);
        } else if (single instanceof jakarta.persistence.criteria.Expression<?> expr) {
            visitIsTrue(expr);
        }
    }

    @Override
    public void visit(final ConjunctionPredicate conjunction) {
        visitLogical(conjunction.getPredicates(), "$and");
    }

    @Override
    public void visit(final DisjunctionPredicate disjunction) {
        visitLogical(disjunction.getPredicates(), "$or");
    }

    private void visitLogical(
        final Collection<? extends IExpression<Boolean>> predicates, final String op) {
        if (predicates.isEmpty()) {
            return;
        }
        if (predicates.size() == 1) {
            visitSinglePredicate(predicates);
            return;
        }
        List<Object> ops = new ArrayList<>(predicates.size());
        query.put(op, ops);
        for (IExpression<Boolean> expression : predicates) {
            Map<String, Object> preQuery = query;
            query = new LinkedHashMap<>();
            ops.add(query);
            if (expression instanceof IPredicate iPredicate) {
                iPredicate.visitPredicate(this);
            }
            query = preQuery;
        }
    }

    @Override
    public void visit(final NegatedPredicate negate) {
        IExpression<Boolean> negated = negate.getNegated();
        Map<String, Object> preQuery = query;
        query = new LinkedHashMap<>();
        ((IPredicate) negated).visitPredicate(this);
        if (query.size() != 1) {
            throw new IllegalStateException("Expected size of 1: Got: " + query);
        }
        Map.Entry<String, Object> propertyPredicate = query.entrySet().iterator().next();
        query = preQuery;
        Object val = propertyPredicate.getValue();
        // Optimize: $not:{$in:[...]} → $nin:[...]
        if (val instanceof Map<?, ?> m && m.size() == 1 && m.containsKey("$in")) {
            query.put(propertyPredicate.getKey(), Collections.singletonMap("$nin", m.get("$in")));
        } else {
            query.put(propertyPredicate.getKey(), Collections.singletonMap("$not", val));
        }
    }

    @Override
    public void visit(final ExistsSubqueryPredicate existsSubqueryPredicate) {
        throw new UnsupportedOperationException("NitriteDB does not support subqueries.");
    }

    @Override
    public void visit(final LikePredicate likePredicate) {
        handleRegexExpression(
            likePredicate.getExpression(),
            likePredicate.isCaseInsensitive(),
            likePredicate.isNegated(),
            false,
            false,
            likePredicate.getPattern(),
            true);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void appendOperatorExpression(
        final Expression<?> leftExpression, final String op, final Expression<?> value) {
        if (leftExpression instanceof BinaryExpression<?> binaryExpression) {
            if (binaryExpression.getType().name().equals("PROD")) {
                throw new UnsupportedOperationException(
                    "NitriteDB does not support $multiply expressions ($expr). "
                    + "Perform multiplication in application code before querying.");
            }
            throw new IllegalStateException(
                "Unsupported binary expression type: " + binaryExpression.getType());
        }
        if (leftExpression instanceof UnaryExpression<?> unaryExpression) {
            if (unaryExpression.getType().name().equals("LENGTH")) {
                throw new UnsupportedOperationException(
                    "NitriteDB does not support string length expressions ($strLenCP / $expr). "
                    + "Filter by length in application code.");
            }
            throw new IllegalStateException(
                "Unsupported unary expression type: " + unaryExpression.getType());
        }
        PersistentPropertyPath propertyPath =
            CriteriaUtils.requireProperty(leftExpression).getPropertyPath();
        appendOperatorExpression(op, value, propertyPath);
    }

    private void appendOperatorExpression(
        final String op, final Expression<?> value, final PersistentPropertyPath propertyPath) {
        if (value
            instanceof
            io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> persistentPropertyPath) {
            PersistentPropertyPath p2 = getRequiredProperty(persistentPropertyPath);
            query.put("$expr", Map.of(op, List.of("$" + propertyPath.getPath(), "$" + p2.getPath())));
            return;
        }
        PersistentEntityUtils.traversePersistentProperties(
            propertyPath,
            (associations, property) -> {
                PersistentPropertyPath ppp = PersistentPropertyPath.of(associations, property);
                String path = getFieldName(ppp);
                query.put(
                    path,
                    Collections.singletonMap(
                        op,
                        valueRepresentation(
                            queryState,
                            propertyPath,
                            ppp,
                            value)));
            });
    }

    /**
     * Handle regex-based string operations (contains, startsWith, endsWith, like, ignoreCase).
     * <p>
     * Builds regex patterns with optional case-insensitive flag {@code (?i)} for ignoreCase operations.
     * For parameterized patterns, the placeholder is embedded in the pattern structure and resolved
     * at runtime (e.g., {@code "(?i).*:0.*"} becomes {@code "(?i).*michael.*"} at execution time).
     *
     * @param leftExpression the property expression
     * @param ignoreCase whether to enable case-insensitive matching
     * @param negated whether to negate the match
     * @param startsWith whether this is a startsWith operation
     * @param endsWith whether this is an endsWith operation
     * @param rightExpression the value expression
     * @param isLike whether this is a LIKE operation
     */
    private void handleRegexExpression(
        final Expression<?> leftExpression,
        final boolean ignoreCase,
        final boolean negated,
        final boolean startsWith,
        final boolean endsWith,
        final Expression<?> rightExpression,
        final boolean isLike) {
        if (leftExpression
            instanceof
            io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> propertyPathExpr) {
            final PersistentPropertyPath propertyPath = propertyPathExpr.getPropertyPath();
            final String fieldName = getFieldName(propertyPath);
            Object regexValue = expressionHandler.handleRegex(
                fieldName, ignoreCase, negated, startsWith, endsWith, rightExpression, isLike, queryState, propertyPath);
            if (regexValue instanceof Map<?, ?> filterMap) {
                query.put(fieldName, negated ? Map.of(NOT, filterMap) : filterMap);
            } else {
                Map<String, Object> fieldFilter = new LinkedHashMap<>();
                fieldFilter.put(REGEX, regexValue);
                query.put(fieldName, negated ? Map.of(NOT, fieldFilter) : fieldFilter);
            }
        }
    }

    private Object valueRepresentation(
        final NitriteQueryState queryState,
        final PersistentPropertyPath propertyPath,
        final Object value) {
        return expressionHandler.resolveValue(queryState, propertyPath, value);
    }

    private Object valueRepresentation(
        final NitriteQueryState queryState,
        final PersistentPropertyPath propertyPath,
        final PersistentPropertyPath persistentPropertyPath,
        final Expression<?> expression) {
        return expressionHandler.resolveValue(queryState, propertyPath, expression);
    }

    // -------------------------------------------------------------------------
    // Static utility methods (also used by NitriteQueryBuilder)
    // -------------------------------------------------------------------------

    static String getFieldName(final PersistentPropertyPath propertyPath) {
        return NitriteFieldNameResolver.getFieldName(propertyPath);
    }

    static BindingParameter.BindingContext newBindingContext(
        @Nullable final PersistentPropertyPath ref) {
        return newBindingContext(ref, ref);
    }

    static BindingParameter.BindingContext newBindingContext(
        @Nullable final PersistentPropertyPath in, @Nullable final PersistentPropertyPath out) {
        return BindingParameter.BindingContext.create()
            .incomingMethodParameterProperty(in)
            .outgoingQueryParameterProperty(out);
    }

    static String asPath(
        final Collection<Association> associations, final PersistentProperty property) {
        return NitriteFieldNameResolver.asPath(associations, property);
    }
}
