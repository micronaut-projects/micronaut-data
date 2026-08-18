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
import io.micronaut.data.model.jpa.criteria.impl.BoundPathParameterExpression;
import io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils;
import io.micronaut.data.model.jpa.criteria.impl.DefaultPersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.impl.IParameterExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.BinaryExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.CastExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.FunctionExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.LiteralExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.UnaryExpression;
import io.micronaut.data.model.jpa.criteria.impl.predicate.BinaryPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.ConjunctionPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.DisjunctionPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.ExistsSubqueryPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.LikePredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.NegatedPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.PredicateBinaryOp;
import io.micronaut.data.model.query.BindingParameter;
import io.micronaut.data.model.query.impl.AdvancedPredicateVisitor;
import io.micronaut.data.nitrite.model.query.NitriteInternalKeys;
import io.micronaut.data.nitrite.model.query.NitriteQueryOperators;
import io.micronaut.data.nitrite.runtime.ValueConverter;
import jakarta.persistence.criteria.Expression;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.ALL;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.AND;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.BETWEEN;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.CONCAT;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.DIVIDE;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.EQ;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.EXISTS;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.EXPR;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.GT;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.GTE;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.IN;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.INTERSECTS;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.LT;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.LTE;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.MULTIPLY;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.NE;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.NEAR;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.NIN;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.OR;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.RIGHT;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.STR_LEN_CP;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.SUBSTR_CP;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.TO_DOUBLE;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.TO_LOWER;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.TO_UPPER;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.WITHIN;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.expression;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.operator;

/**
 * Translates JPA Criteria predicates into a NitriteDB JSON filter map.
 *
 * <p><strong>Supported Operators:</strong> {@code $ne}, {@code $regex}, {@code $exists},
 * {@code $in}, {@code $nin}, {@code $like}, {@code $not}, {@code $empty}, {@code $all}.
 *
 * <p><strong>Unsupported Features:</strong>
 * <ul>
 *   <li>Full-text search ({@code $text}) requires dedicated indexing and will throw an exception.</li>
 *   <li>Arithmetic operations in queries (e.g., {@code sum}, {@code diff})
 *       are not supported by Nitrite and will intentionally throw {@link IllegalStateException}.</li>
 * </ul>
 *
 * @since 5.2.0
 */
public final class NitritePredicateVisitor implements AdvancedPredicateVisitor<PersistentPropertyPath> {

    private static final String REGEX = NitriteQueryOperators.REGEX;
    private static final String NOT = NitriteQueryOperators.NOT;

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
        // Only reachable with a multi-@Id composite identity (no single @EmbeddedId property):
        // PersistentEntityRoot.id() yields an IdExpression (which routes here) solely in that
        // case — a single identity property (plain @Id or @EmbeddedId) collapses to a property
        // path handled by visitEquals. Mirrors AbstractSqlLikeQueryBuilder.visitIdEquals: expand
        // whole-identity equality into a per-property AND, each bound to the matching sub-path
        // of the id parameter. Only supported against a bound parameter (there is no single
        // value type to compare a multi-@Id identity against as a literal).
        if (!(expression instanceof IParameterExpression<?> parameterExpression)) {
            throw new IllegalStateException("Composite identity expressions can only be used with parameters");
        }
        new ConjunctionPredicate(Arrays.stream(persistentEntity.getCompositeIdentity())
            .map(prop -> {
                PersistentPropertyPath propertyPath = PersistentPropertyPath.of(Collections.emptyList(), prop, prop.getName());
                return new BinaryPredicate(
                    new DefaultPersistentPropertyPath<>(propertyPath),
                    new BoundPathParameterExpression<>(parameterExpression, propertyPath),
                    PredicateBinaryOp.EQUALS);
            })
            .toList()).visitPredicate(this);
    }

    @Override
    public void visitEquals(
        final Expression<?> leftExpression,
        final Expression<?> rightExpression,
        final boolean ignoreCase) {
        if (ignoreCase) {
            handleRegexExpression(leftExpression, true, false, false, false, rightExpression, false);
        } else {
            appendOperatorExpression(leftExpression, EQ, rightExpression);
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
            appendOperatorExpression(leftExpression, NE, rightExpression);
        }
    }

    @Override
    public void visitGreaterThan(
        final Expression<?> leftExpression, final Expression<?> rightExpression) {
        appendOperatorExpression(leftExpression, GT, rightExpression);
    }

    @Override
    public void visitGreaterThanOrEquals(
        final Expression<?> leftExpression, final Expression<?> rightExpression) {
        appendOperatorExpression(leftExpression, GTE, rightExpression);
    }

    @Override
    public void visitLessThan(
        final Expression<?> leftExpression, final Expression<?> rightExpression) {
        appendOperatorExpression(leftExpression, LT, rightExpression);
    }

    @Override
    public void visitLessThanOrEquals(
        final Expression<?> leftExpression, final Expression<?> rightExpression) {
        appendOperatorExpression(leftExpression, LTE, rightExpression);
    }

    @Override
    public void visitIsNull(final Expression<?> expression) {
        PersistentPropertyPath propertyPath =
            CriteriaUtils.requireProperty(expression).getPropertyPath();
        PersistentEntityUtils.traversePersistentProperties(
            propertyPath,
            (associations, property) -> {
                String path = getFieldNameForNullCheck(associations, property);
                putFieldOperator(path, EQ, null);
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
                putFieldOperator(path, NE, null); // notEq(null)
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
                putFieldOperator(path, EQ, true);
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
                putFieldOperator(path, EQ, false);
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
                query.put(OR, List.of(
                    fieldOperator(path, EQ, ""),
                    fieldOperator(path, EXISTS, false)
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
                query.put(AND, List.of(
                    fieldOperator(path, NE, ""),
                    fieldOperator(path, EXISTS, true)
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
        if (isComputedExpression(expression)) {
            PersistentPropertyPath ctx = requirePropertyOperand(expression);
            Object valueExpr = requireExprOperand(expression, ctx);
            List<Object> resolvedValues = values == null ? Collections.emptyList() : values.stream()
                .map(val -> val instanceof Expression<?> valueExpression
                    ? requireExprOperand(valueExpression, ctx)
                    : valueRepresentation(queryState, ctx, val))
                .toList();
            putExpressionOperator(negated ? NIN : IN, valueExpr, resolvedValues);
            return;
        }
        PersistentPropertyPath propertyPath =
            CriteriaUtils.requireProperty(expression).getPropertyPath();
        String fieldName = getFieldName(propertyPath);

        // Handle null or empty collection - IN with no values matches nothing, NOT IN matches all.
        // (values == null is defensive: the criteria API always supplies a non-null collection;
        // the empty path is reachable and covered.)
        if (values == null || values.isEmpty()) {
            if (!negated) {
                // IN with empty set matches nothing - add impossible condition
                putFieldOperator("_id", EQ, null);
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
                resolvedValues = List.of(NitriteInternalKeys.QUERY_PARAMETER_PREFIX + index);
            } else {
                resolvedValues = Collections.singletonList(valueRepresentation(queryState, propertyPath, singleValue));
            }
        } else {
            resolvedValues = values.stream()
                .map(val -> valueRepresentation(queryState, propertyPath, val))
                .toList();
        }

        putFieldOperator(fieldName, negated ? NIN : IN, resolvedValues);
    }

    @Override
    public void visitInBetween(
        final Expression<?> value,
        final Expression<?> from,
        final Expression<?> to,
        final boolean negated) {
        if (value instanceof BinaryExpression<?> || value instanceof UnaryExpression<?>) {
            PersistentPropertyPath ctx = requirePropertyOperand(value);
            Object valueExpr = exprOperand(value, ctx);
            Object fromExpr = exprOperand(from, ctx);
            Object toExpr = exprOperand(to, ctx);
            String lowerOp = negated ? LT : GTE;
            String upperOp = negated ? GT : LTE;
            String joinOp = negated ? OR : AND;
            query.put(joinOp, List.of(
                expression(lowerOp, List.of(valueExpr, fromExpr)),
                expression(upperOp, List.of(valueExpr, toExpr))
            ));
            return;
        }
        PersistentPropertyPath propertyPath = CriteriaUtils.requireProperty(value).getPropertyPath();
        // Use Nitrite's native $between operator instead of decomposing to $gte + $lte
        List<Object> betweenValues = Arrays.asList(
            valueRepresentation(queryState, propertyPath, from),
            valueRepresentation(queryState, propertyPath, to)
        );
        // negated is always false: visit(BetweenPredicate) hardcodes it, and a negated
        // between is wrapped externally in a NegatedPredicate ($not) rather than flipped here.
        PersistentEntityUtils.traversePersistentProperties(
            propertyPath,
            (associations, property) -> {
                String path = asPath(associations, property);
                putFieldOperator(path, BETWEEN, betweenValues);
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
        putFieldOperator(fieldName, REGEX, regexValue);
    }

    @Override
    public void visitGeoWithin(final Expression<?> leftExpression, final Expression<?> expression) {
        PersistentPropertyPath propertyPath =
            CriteriaUtils.requireProperty(leftExpression).getPropertyPath();
        String fieldName = getFieldName(propertyPath);
        Object geoValue = valueRepresentation(queryState, propertyPath, expression);
        putFieldOperator(fieldName, WITHIN, geoValue);
    }

    @Override
    public void visitGeoIntersects(final Expression<?> leftExpression, final Expression<?> expression) {
        PersistentPropertyPath propertyPath =
            CriteriaUtils.requireProperty(leftExpression).getPropertyPath();
        String fieldName = getFieldName(propertyPath);
        Object geoValue = valueRepresentation(queryState, propertyPath, expression);
        putFieldOperator(fieldName, INTERSECTS, geoValue);
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
        Object distValue = valueRepresentation(queryState, propertyPath, distanceExpression);
        Map<String, Object> nearMap = new LinkedHashMap<>();
        nearMap.put("center", geoValue);
        nearMap.put("distance", distValue);
        putFieldOperator(fieldName, NEAR, nearMap);
    }

    @Override
    public void visitArrayContains(
        final Expression<?> leftExpression, final Expression<?> expression) {
        PersistentPropertyPath propertyPath =
            CriteriaUtils.requireProperty(leftExpression).getPropertyPath();
        String fieldName = getFieldName(propertyPath);
        List<Object> criteriaValues = expressionHandler.resolveCollectionValue(queryState, propertyPath, expression);
        putFieldOperator(fieldName, ALL, criteriaValues);
    }

    private void visitSinglePredicate(final Collection<? extends IExpression<Boolean>> predicates) {
        IExpression<Boolean> single = predicates.iterator().next();
        if (single instanceof IPredicate iPredicate) {
            iPredicate.visitPredicate(this);
        } else if (single != null) {
            visitIsTrue(single);
        }
    }

    @Override
    public void visit(final ConjunctionPredicate conjunction) {
        visitLogical(conjunction.getPredicates(), AND);
    }

    @Override
    public void visit(final DisjunctionPredicate disjunction) {
        visitLogical(disjunction.getPredicates(), OR);
    }

    private void visitLogical(
        final Collection<? extends IExpression<Boolean>> predicates, final String op) {
        // Defensive: a Conjunction/Disjunction from the criteria API always has >= 1 predicate.
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
        if (query.isEmpty()) {
            query = preQuery;
            query.put(NOT, Collections.emptyMap());
            return;
        }
        // Defensive: a single negated predicate emits exactly one top-level entry; a multi-entry
        // result would mean an unsupported negation shape.
        if (query.size() != 1) {
            throw new IllegalStateException("Expected size of 1: Got: " + query);
        }
        Map.Entry<String, Object> propertyPredicate = query.entrySet().iterator().next();
        query = preQuery;
        Object val = propertyPredicate.getValue();
        // Optimize: $not:{$in:[...]} → $nin:[...]
        if (val instanceof Map<?, ?> m && m.size() == 1 && m.containsKey(IN)) {
            putFieldOperator(propertyPredicate.getKey(), NIN, m.get(IN));
        } else if (isTopLevelOperator(propertyPredicate.getKey())) {
            query.put(NOT, Map.of(propertyPredicate.getKey(), val));
        } else {
            putFieldOperator(propertyPredicate.getKey(), NOT, val);
        }
    }

    @Override
    public void visit(final ExistsSubqueryPredicate existsSubqueryPredicate) {
        throw new UnsupportedOperationException("NitriteDB does not support subqueries.");
    }

    @Override
    public void visit(final LikePredicate likePredicate) {
        handleLikeExpression(likePredicate);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void appendOperatorExpression(
        final Expression<?> leftExpression, final String op, final Expression<?> value) {
        if (isComputedExpression(leftExpression)) {
            PersistentPropertyPath ctx = requirePropertyOperand(leftExpression);
            appendExprComparison(requireExprOperand(leftExpression, ctx), op, value, ctx);
            return;
        }
        if (!(leftExpression instanceof io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?>)) {
            appendPropertylessExprComparison(leftExpression, op, value);
            return;
        }
        PersistentPropertyPath propertyPath =
            CriteriaUtils.requireProperty(leftExpression).getPropertyPath();
        appendOperatorExpression(op, value, propertyPath);
    }

    private void appendPropertylessExprComparison(
        final Expression<?> leftExpression,
        final String op,
        final Expression<?> value) {
        putExpressionOperator(op, propertylessOperand(leftExpression), propertylessOperand(value));
    }

    /**
     * Appends a {@code $expr} comparison of a computed left-hand value tree against the
     * right-hand value expression.
     */
    private void appendExprComparison(
        final Object leftExprTree, final String op, final Expression<?> value,
        final PersistentPropertyPath bindingContextPath) {
        Object valueExpr = requireExprOperand(value, bindingContextPath);
        putExpressionOperator(op, leftExprTree, valueExpr);
    }

    /**
     * Resolves a computed operand to either a field reference ({@code "$fieldName"}), another
     * expression tree, or a literal/bound-parameter value.
     */
    private @Nullable Object exprOperand(final Expression<?> expr, final PersistentPropertyPath bindingContextPath) {
        if (expr instanceof io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> ppp) {
            return "$" + getFieldName(getRequiredProperty(ppp));
        }
        if (expr instanceof UnaryExpression<?> unaryExpression) {
            Object inner = requireExprOperand(unaryExpression.getExpression(), bindingContextPath);
            return switch (unaryExpression.getType()) {
                case LENGTH -> Map.of(STR_LEN_CP, inner);
                case LOWER -> Map.of(TO_LOWER, inner);
                case UPPER -> Map.of(TO_UPPER, inner);
                default -> throw new IllegalStateException(
                    "Unsupported unary expression type: " + unaryExpression.getType());
            };
        }
        if (expr instanceof BinaryExpression<?> binaryExpression) {
            List<Object> operands = Arrays.asList(
                requireExprOperand(binaryExpression.getLeft(), bindingContextPath),
                requireExprOperand(binaryExpression.getRight(), bindingContextPath));
            return switch (binaryExpression.getType()) {
                case CONCAT -> Map.of(CONCAT, operands);
                case PROD -> Map.of(MULTIPLY, operands);
                case QUOT -> Map.of(DIVIDE, operands);
                default -> throw new IllegalStateException(
                    "Unsupported binary expression type: " + binaryExpression.getType());
            };
        }
        if (expr instanceof FunctionExpression<?> functionExpression) {
            return functionOperand(functionExpression, bindingContextPath);
        }
        if (expr instanceof CastExpression<?> castExpression) {
            Object inner = requireExprOperand(castExpression.getExpression(), bindingContextPath);
            if (Number.class.isAssignableFrom(castExpression.getJavaType()) || castExpression.getJavaType().isPrimitive()) {
                return Map.of(TO_DOUBLE, inner);
            }
            return inner;
        }
        return valueRepresentation(queryState, bindingContextPath, expr);
    }

    private Object functionOperand(final FunctionExpression<?> functionExpression, final PersistentPropertyPath bindingContextPath) {
        List<Expression<?>> expressions = functionExpression.getExpressions();
        return switch (functionExpression.getName().toUpperCase(Locale.ROOT)) {
            case "CONCAT" -> Map.of(CONCAT, expressions.stream()
                .map(expression -> requireExprOperand(expression, bindingContextPath))
                .toList());
            case "LENGTH" -> Map.of(STR_LEN_CP, requireExprOperand(expressions.getFirst(), bindingContextPath));
            case "LOWER" -> Map.of(TO_LOWER, requireExprOperand(expressions.get(0), bindingContextPath));
            case "UPPER" -> Map.of(TO_UPPER, requireExprOperand(expressions.get(0), bindingContextPath));
            case "LEFT" -> Map.of(SUBSTR_CP, List.of(
                requireExprOperand(expressions.get(0), bindingContextPath),
                0,
                requireExprOperand(expressions.get(1), bindingContextPath)));
            case "RIGHT" -> Map.of(RIGHT, List.of(
                requireExprOperand(expressions.get(0), bindingContextPath),
                requireExprOperand(expressions.get(1), bindingContextPath)));
            default -> throw new IllegalStateException(
                "Unsupported function expression: " + functionExpression.getName());
        };
    }

    private Object requireExprOperand(final Expression<?> expr, final PersistentPropertyPath bindingContextPath) {
        return Objects.requireNonNull(
            exprOperand(expr, bindingContextPath),
            "Expression operand cannot resolve to null");
    }

    private @Nullable Object propertylessOperand(final Expression<?> expr) {
        if (expr instanceof LiteralExpression<?> literal) {
            return ValueConverter.toFilterValueStatic(unwrapLiteral(literal));
        }
        if (expr instanceof BindingParameter bindingParameter) {
            int index = queryState.pushParameter(bindingParameter, BindingParameter.BindingContext.create());
            return Map.of(NitriteInternalKeys.QUERY_PARAMETER_PLACEHOLDER, index);
        }
        return expr;
    }

    private static @Nullable Object unwrapLiteral(LiteralExpression<?> literal) {
        Object value = literal.getValue();
        while (value instanceof LiteralExpression<?> nested) {
            value = nested.getValue();
        }
        return value;
    }

    private PersistentPropertyPath requirePropertyOperand(final Expression<?> expression) {
        if (expression instanceof io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> ppp) {
            return getRequiredProperty(ppp);
        }
        if (expression instanceof UnaryExpression<?> unaryExpression) {
            return requirePropertyOperand(unaryExpression.getExpression());
        }
        if (expression instanceof FunctionExpression<?> functionExpression) {
            for (Expression<?> nestedExpression : functionExpression.getExpressions()) {
                try {
                    return requirePropertyOperand(nestedExpression);
                } catch (IllegalStateException e) {
                    // Try the next operand.
                }
            }
        }
        if (expression instanceof CastExpression<?> castExpression) {
            return requirePropertyOperand(castExpression.getExpression());
        }
        if (expression instanceof BinaryExpression<?> binaryExpression) {
            try {
                return requirePropertyOperand(binaryExpression.getLeft());
            } catch (IllegalStateException e) {
                return requirePropertyOperand(binaryExpression.getRight());
            }
        }
        throw new IllegalStateException("Computed expressions require at least one property-path operand.");
    }

    private static boolean isComputedExpression(final Expression<?> expression) {
        return expression instanceof BinaryExpression<?>
            || expression instanceof UnaryExpression<?>
            || expression instanceof FunctionExpression<?>
            || expression instanceof CastExpression<?>;
    }

    private void appendOperatorExpression(
        final String op, final Expression<?> value, final PersistentPropertyPath propertyPath) {
        if (value
            instanceof
            io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> persistentPropertyPath) {
            PersistentPropertyPath p2 = getRequiredProperty(persistentPropertyPath);
            putExpressionOperator(op, "$" + getExpressionFieldName(propertyPath), "$" + getExpressionFieldName(p2));
            return;
        }
        PersistentEntityUtils.traversePersistentProperties(
            propertyPath,
            (associations, property) -> {
                PersistentPropertyPath ppp = PersistentPropertyPath.of(associations, property);
                String path = getFieldName(ppp);
                putFieldOperator(path, op, valueRepresentation(queryState, propertyPath, value));
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
        handleRegexExpression(leftExpression, ignoreCase, negated, startsWith, endsWith, rightExpression, isLike, null);
    }

    private void handleRegexExpression(
        final Expression<?> leftExpression,
        final boolean ignoreCase,
        final boolean negated,
        final boolean startsWith,
        final boolean endsWith,
        final Expression<?> rightExpression,
        final boolean isLike,
        @Nullable final Expression<Character> escapeExpression) {
        if (leftExpression
            instanceof
            io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> propertyPathExpr) {
            final PersistentPropertyPath propertyPath = propertyPathExpr.getPropertyPath();
            final String fieldName = getFieldName(propertyPath);
            // Both handler impls (Runtime + Compile) always return a String regex value.
            Object regexValue = expressionHandler.handleRegex(
                fieldName, ignoreCase, negated, startsWith, endsWith, rightExpression, isLike, escapeExpression, queryState, propertyPath);
            Map<String, Object> fieldFilter = operator(REGEX, regexValue);
            query.put(fieldName, negated ? Map.of(NOT, fieldFilter) : fieldFilter);
        }
    }

    private void handleLikeExpression(final LikePredicate likePredicate) {
        if (likePredicate.getExpression()
            instanceof
            io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> propertyPathExpr) {
            PersistentPropertyPath propertyPath = propertyPathExpr.getPropertyPath();
            String fieldName = getFieldName(propertyPath);
            Map<String, Object> pattern = new LinkedHashMap<>(3);
            pattern.put(NitriteInternalKeys.LIKE_PATTERN, valueRepresentation(queryState, propertyPath, likePredicate.getPattern()));
            Object escape = likePredicate.getEscapeChar() == null
                ? null
                : valueRepresentation(queryState, propertyPath, likePredicate.getEscapeChar());
            if (escape != null) {
                pattern.put(NitriteInternalKeys.LIKE_ESCAPE, escape);
            }
            if (likePredicate.isCaseInsensitive()) {
                pattern.put(NitriteInternalKeys.LIKE_IGNORE_CASE, true);
            }
            Map<String, Object> fieldFilter = operator(REGEX, pattern);
            query.put(fieldName, likePredicate.isNegated() ? Map.of(NOT, fieldFilter) : fieldFilter);
        }
    }

    private static boolean isTopLevelOperator(String key) {
        return AND.equals(key) || OR.equals(key) || EXPR.equals(key) || NOT.equals(key);
    }

    private void putFieldOperator(final String fieldName, final String op, @Nullable final Object value) {
        query.put(fieldName, operator(op, value));
    }

    private void putExpressionOperator(final String op, @Nullable final Object left, @Nullable final Object right) {
        query.put(EXPR, operator(op, Arrays.asList(left, right)));
    }

    private static Map<String, Object> fieldOperator(final String fieldName, final String op, @Nullable final Object value) {
        return Map.of(fieldName, operator(op, value));
    }

    private @Nullable Object valueRepresentation(
        final NitriteQueryState queryState,
        final PersistentPropertyPath propertyPath,
        final Object value) {
        return expressionHandler.resolveValue(queryState, propertyPath, value);
    }

    private @Nullable Object valueRepresentation(
        final NitriteQueryState queryState,
        final PersistentPropertyPath propertyPath,
        final Expression<?> expression) {
        return expressionHandler.resolveValue(queryState, propertyPath, expression);
    }

    // -------------------------------------------------------------------------
    // Static utility methods (also used by NitriteQueryBuilder)
    // -------------------------------------------------------------------------

    static String getFieldName(final PersistentPropertyPath propertyPath) {
        return NitriteFieldNameResolver.getFieldName(propertyPath);
    }

    /**
     * Resolves the field name used inside an {@code $expr} comparison. Association segments keep
     * their logical names because those are the aliases the lookup stages produce, while the
     * terminal property is mapped to its persisted name so that {@code @MappedProperty} fields
     * reference a field that exists in the stored document.
     *
     * @param propertyPath the property path
     * @return the field name to compare against
     */
    private static String getExpressionFieldName(final PersistentPropertyPath propertyPath) {
        if (propertyPath.getAssociations().stream().allMatch(Association::isEmbedded)) {
            return getFieldName(propertyPath);
        }
        String logicalPath = propertyPath.getPath();
        String propertyName = propertyPath.getProperty().getName();
        return logicalPath.substring(0, logicalPath.length() - propertyName.length())
            + propertyPath.getProperty().getPersistedName();
    }

    static BindingParameter.BindingContext newBindingContext(
        @Nullable final PersistentPropertyPath ref) {
        return newBindingContext(ref, ref);
    }

    /**
     * Creates a binding context for a root-level property, which has no owning associations.
     *
     * @param property the property being bound, {@code null} when it is not mapped
     * @return the binding context
     */
    static BindingParameter.BindingContext newBindingContext(@Nullable final PersistentProperty property) {
        return newBindingContext(
            property == null ? null : PersistentPropertyPath.of(Collections.emptyList(), property, property.getName()));
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
