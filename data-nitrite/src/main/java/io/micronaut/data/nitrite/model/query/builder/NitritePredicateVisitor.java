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
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentEntityUtils;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.IExpression;
import io.micronaut.data.model.jpa.criteria.IPredicate;
import io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils;
import io.micronaut.data.model.jpa.criteria.impl.expression.BinaryExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.LiteralExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.UnaryExpression;
import io.micronaut.data.model.jpa.criteria.impl.predicate.ConjunctionPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.DisjunctionPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.ExistsSubqueryPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.LikePredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.NegatedPredicate;
import io.micronaut.data.model.query.BindingParameter;
import io.micronaut.data.model.query.impl.AdvancedPredicateVisitor;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Translates JPA Criteria predicates into a NitriteDB JSON filter map. */
@SuppressWarnings({"rawtypes", "unchecked"})
final class NitritePredicateVisitor implements AdvancedPredicateVisitor<PersistentPropertyPath> {

  static final String ID_FIELD = "id";
  private static final String REGEX = "$regex";
  private static final String NOT = "$not";

  private final PersistentEntity persistentEntity;
  private final NitriteQueryState queryState;
  private Map<String, Object> query;

  NitritePredicateVisitor(final NitriteQueryState queryState) {
    this(queryState, new LinkedHashMap<>());
  }

  NitritePredicateVisitor(final NitriteQueryState queryState, final Map<String, Object> query) {
    this.queryState = queryState;
    this.query = query;
    persistentEntity = queryState.getEntity();
  }

  public Map<String, Object> getQuery() {
    return query;
  }

  // -------------------------------------------------------------------------
  // AdvancedPredicateVisitor implementation
  // -------------------------------------------------------------------------

  @Override
  public PersistentPropertyPath getRequiredProperty(
      final io.micronaut.data.model.jpa.criteria.PersistentPropertyPath persistentPropertyPath) {
    return (PersistentPropertyPath) (Object) persistentPropertyPath.getPropertyPath();
  }

  @Override
  public void visitIdEquals(final jakarta.persistence.criteria.Expression expression) {
    if (persistentEntity.hasCompositeIdentity()) {
      throw new IllegalStateException("Composite ID not supported!");
    } else if (persistentEntity.hasIdentity()) {
      query.put(
          ID_FIELD,
          valueRepresentation(
              new PersistentPropertyPath(persistentEntity.getIdentity()), expression));
    } else {
      throw new IllegalStateException("No ID found for entity: " + persistentEntity.getName());
    }
  }

  @Override
  public void visitEquals(
      final jakarta.persistence.criteria.Expression leftExpression,
      final jakarta.persistence.criteria.Expression rightExpression,
      final boolean ignoreCase) {
    if (ignoreCase) {
      handleRegexExpression(leftExpression, true, false, false, false, rightExpression, false);
    } else {
      appendOperatorExpression(leftExpression, "$eq", rightExpression);
    }
  }

  @Override
  public void visitNotEquals(
      final jakarta.persistence.criteria.Expression leftExpression,
      final jakarta.persistence.criteria.Expression rightExpression,
      final boolean ignoreCase) {
    if (ignoreCase) {
      handleRegexExpression(leftExpression, true, true, false, false, rightExpression, false);
    } else {
      appendOperatorExpression(leftExpression, "$ne", rightExpression);
    }
  }

  @Override
  public void visitGreaterThan(
      final jakarta.persistence.criteria.Expression leftExpression, final jakarta.persistence.criteria.Expression rightExpression) {
    appendOperatorExpression(leftExpression, "$gt", rightExpression);
  }

  @Override
  public void visitGreaterThanOrEquals(
      final jakarta.persistence.criteria.Expression leftExpression, final jakarta.persistence.criteria.Expression rightExpression) {
    appendOperatorExpression(leftExpression, "$gte", rightExpression);
  }

  @Override
  public void visitLessThan(
      final jakarta.persistence.criteria.Expression leftExpression, final jakarta.persistence.criteria.Expression rightExpression) {
    appendOperatorExpression(leftExpression, "$lt", rightExpression);
  }

  @Override
  public void visitLessThanOrEquals(
      final jakarta.persistence.criteria.Expression leftExpression, final jakarta.persistence.criteria.Expression rightExpression) {
    appendOperatorExpression(leftExpression, "$lte", rightExpression);
  }

  @Override
  public void visitIsNull(final jakarta.persistence.criteria.Expression expression) {
    PersistentPropertyPath propertyPath =
        CriteriaUtils.requireProperty(expression).getPropertyPath();
    PersistentEntityUtils.traversePersistentProperties(
        propertyPath,
        (associations, property) -> {
          String path = getFieldNameForNullCheck(associations, property);
          query.put(path, null); // Nitrite: FluentFilter.where(field).eq(null)
        });
  }

  @Override
  public void visitIsNotNull(final jakarta.persistence.criteria.Expression expression) {
    PersistentPropertyPath propertyPath =
        CriteriaUtils.requireProperty(expression).getPropertyPath();
    PersistentEntityUtils.traversePersistentProperties(
        propertyPath,
        (associations, property) -> {
          String path = getFieldNameForNullCheck(associations, property);
          query.put(path, Collections.singletonMap("$ne", null)); // notEq(null)
        });
  }

  @Override
  public void visitIsTrue(final jakarta.persistence.criteria.Expression expression) {
    PersistentPropertyPath propertyPath =
        CriteriaUtils.requireProperty(expression).getPropertyPath();
    PersistentEntityUtils.traversePersistentProperties(
        propertyPath,
        (associations, property) -> {
          String path = asPath(associations, property);
          query.put(path, true); // Nitrite: FluentFilter.where(field).eq(true)
        });
  }

  @Override
  public void visitIsFalse(final jakarta.persistence.criteria.Expression expression) {
    PersistentPropertyPath propertyPath =
        CriteriaUtils.requireProperty(expression).getPropertyPath();
    PersistentEntityUtils.traversePersistentProperties(
        propertyPath,
        (associations, property) -> {
          String path = asPath(associations, property);
          query.put(path, false); // Nitrite: FluentFilter.where(field).eq(false)
        });
  }

  @Override
  public void visitIsEmpty(final jakarta.persistence.criteria.Expression expression) {
    PersistentPropertyPath propertyPath =
        CriteriaUtils.requireProperty(expression).getPropertyPath();
    PersistentEntityUtils.traversePersistentProperties(
        propertyPath,
        (associations, property) -> {
          String path = asPath(associations, property);
          query.put(path, Collections.singletonMap("$empty", true));
        });
  }

  @Override
  public void visitIsNotEmpty(final jakarta.persistence.criteria.Expression expression) {
    PersistentPropertyPath propertyPath =
        CriteriaUtils.requireProperty(expression).getPropertyPath();
    PersistentEntityUtils.traversePersistentProperties(
        propertyPath,
        (associations, property) -> {
          String path = asPath(associations, property);
          query.put(path, Collections.singletonMap("$empty", false));
        });
  }

  @Override
  public void visitIn(final jakarta.persistence.criteria.Expression expression, final Collection values, final boolean negated) {
    PersistentPropertyPath propertyPath =
        CriteriaUtils.requireProperty(expression).getPropertyPath();
    query.put(
        getFieldName(propertyPath),
        Map.of(
            negated ? "$nin" : "$in",
            values.stream()
                .map(val -> valueRepresentation(propertyPath, val))
                .toList()));
  }

  @Override
  public void visitInBetween(
      final jakarta.persistence.criteria.Expression value,
      final jakarta.persistence.criteria.Expression from,
      final jakarta.persistence.criteria.Expression to,
      final boolean negated) {
    PersistentPropertyPath propertyPath = CriteriaUtils.requireProperty(value).getPropertyPath();
    Map<String, Object> betweenOp = new LinkedHashMap<>();
    betweenOp.put("$gte", valueRepresentation(propertyPath, from));
    betweenOp.put("$lte", valueRepresentation(propertyPath, to));
    PersistentEntityUtils.traversePersistentProperties(
        propertyPath,
        (associations, property) -> {
          String path = asPath(associations, property);
          query.put(path, negated ? Map.of(NOT, betweenOp) : betweenOp);
        });
  }

  @Override
  public void visitContains(
      final jakarta.persistence.criteria.Expression leftExpression,
      final jakarta.persistence.criteria.Expression rightExpression,
      final boolean ignoreCase) {
    handleRegexExpression(leftExpression, ignoreCase, false, false, false, rightExpression, false);
  }

  @Override
  public void visitEndsWith(
      final jakarta.persistence.criteria.Expression leftExpression,
      final jakarta.persistence.criteria.Expression rightExpression,
      final boolean ignoreCase) {
    handleRegexExpression(leftExpression, ignoreCase, false, false, true, rightExpression, false);
  }

  @Override
  public void visitStartsWith(
      final jakarta.persistence.criteria.Expression leftExpression,
      final jakarta.persistence.criteria.Expression rightExpression,
      final boolean ignoreCase) {
    handleRegexExpression(leftExpression, ignoreCase, false, true, false, rightExpression, false);
  }

  @Override
  public void visitRegexp(final jakarta.persistence.criteria.Expression leftExpression, final jakarta.persistence.criteria.Expression rightExpression) {
    jakarta.persistence.criteria.Expression value = rightExpression;
    if (rightExpression instanceof LiteralExpression literalExpression
        && literalExpression.getValue() instanceof String pattern) {
      value = new LiteralExpression(new RegexPattern(pattern));
    }
    appendOperatorExpression(leftExpression, REGEX, value);
  }

  @Override
  public void visitArrayContains(
      final jakarta.persistence.criteria.Expression leftExpression, final jakarta.persistence.criteria.Expression expression) {
    throw new UnsupportedOperationException("NitriteDB does not support arrayContains.");
  }

  @Override
  public void visit(final ConjunctionPredicate conjunction) {
    Collection<? extends IExpression<Boolean>> predicates = conjunction.getPredicates();
    if (predicates.isEmpty()) {
      return;
    }
    if (predicates.size() == 1) {
      ((IPredicate) predicates.iterator().next()).visitPredicate(this);
      return;
    }
    List<Object> ops = new ArrayList<>(predicates.size());
    query.put("$and", ops);
    visitConjunctionPredicate(predicates, ops);
  }

  private void visitConjunctionPredicate(
      final Collection<? extends IExpression<Boolean>> predicates, final List<Object> ops) {
    for (IExpression<Boolean> expression : predicates) {
      if (expression instanceof ConjunctionPredicate conjunctionPredicate) {
        visitConjunctionPredicate(conjunctionPredicate.getPredicates(), ops);
      } else {
        Map<String, Object> preQuery = query;
        query = new LinkedHashMap<>();
        ops.add(query);
        ((IPredicate) expression).visitPredicate(this);
        query = preQuery;
      }
    }
  }

  @Override
  public void visit(final DisjunctionPredicate disjunction) {
    Collection<? extends IExpression<Boolean>> predicates = disjunction.getPredicates();
    if (predicates.isEmpty()) {
      return;
    }
    if (predicates.size() == 1) {
      ((IPredicate) predicates.iterator().next()).visitPredicate(this);
      return;
    }
    List<Object> ops = new ArrayList<>(predicates.size());
    query.put("$or", ops);
    visitDisjunctionPredicate(predicates, ops);
  }

  private void visitDisjunctionPredicate(
      final Collection<? extends IExpression<Boolean>> predicates, final List<Object> ops) {
    for (IExpression<Boolean> expression : predicates) {
      Map<String, Object> preQuery = query;
      query = new LinkedHashMap<>();
      ops.add(query);
      if (expression instanceof DisjunctionPredicate disjunctionPredicate) {
        visitDisjunctionPredicate(disjunctionPredicate.getPredicates(), ops);
      } else {
        ((IPredicate) expression).visitPredicate(this);
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
    final Map<String, Object> negatedPropertyPredicate =
        Map.of("$not", propertyPredicate.getValue());
    query = preQuery;
    query.put(propertyPredicate.getKey(), negatedPropertyPredicate);
  }

  @Override
  public void visit(final ExistsSubqueryPredicate existsSubqueryPredicate) {
    throw new UnsupportedOperationException("NitriteDB does not support subqueries.");
  }

  @Override
  public void visit(final LikePredicate likePredicate) {
    jakarta.persistence.criteria.Expression pattern = likePredicate.getPattern();
    if (pattern instanceof LiteralExpression literalExpression
        && literalExpression.getValue() instanceof String patternString) {
      patternString = patternString.replace("_", ".").replace("%", ".*");
      pattern = new LiteralExpression(patternString);
    }
    handleRegexExpression(
        likePredicate.getExpression(),
        likePredicate.isCaseInsensitive(),
        likePredicate.isNegated(),
        false,
        false,
        pattern,
        true);
  }

  // -------------------------------------------------------------------------
  // Private helpers
  // -------------------------------------------------------------------------

  private void appendOperatorExpression(
      final jakarta.persistence.criteria.Expression leftExpression, final String op, final jakarta.persistence.criteria.Expression value) {
    if (leftExpression instanceof BinaryExpression binaryExpression) {
      if ("PROD".equals(binaryExpression.getType().name())) {
        throw new UnsupportedOperationException(
            "NitriteDB does not support $multiply expressions ($expr). "
                + "Perform multiplication in application code before querying.");
      }
      throw new IllegalStateException(
          "Unsupported binary expression type: " + binaryExpression.getType());
    }
    if (leftExpression instanceof UnaryExpression unaryExpression) {
      if ("LENGTH".equals(unaryExpression.getType().name())) {
        throw new UnsupportedOperationException(
            "NitriteDB does not support string length expressions ($strLenCP / $expr). "
                + "Filter by length in application code.");
      }
      throw new IllegalStateException(
          "Unsupported unary expression type: " + unaryExpression.getType());
    }
    
    // Explicit detection for triggers that might be missed by simple instanceof check
    if (leftExpression instanceof IExpression<?> iExpr) {
        String typeName = iExpr.getClass().getSimpleName();
        if (typeName.contains("Length") || typeName.contains("Size")) {
             throw new UnsupportedOperationException("NitriteDB does not support string length/size expressions.");
        }
    }

    PersistentPropertyPath propertyPath =
        CriteriaUtils.requireProperty(leftExpression).getPropertyPath();
    appendOperatorExpression(op, value, propertyPath);
  }

  private void appendOperatorExpression(
      final String op, final jakarta.persistence.criteria.Expression value, final PersistentPropertyPath propertyPath) {
    if (value
        instanceof
        io.micronaut.data.model.jpa.criteria.PersistentPropertyPath persistentPropertyPath) {
      PersistentPropertyPath p2 = (PersistentPropertyPath) (Object) persistentPropertyPath.getPropertyPath();
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
                      ppp,
                      value)));
        });
  }

  private void handleRegexExpression(
      final jakarta.persistence.criteria.Expression leftExpression,
      final boolean ignoreCase,
      final boolean negated,
      final boolean startsWith,
      final boolean endsWith,
      final jakarta.persistence.criteria.Expression rightExpression,
      final boolean isLike) {
    if (leftExpression
        instanceof
        io.micronaut.data.model.jpa.criteria.PersistentPropertyPath propertyPathExpr) {
      final PersistentPropertyPath propertyPath = propertyPathExpr.getPropertyPath();
      final String fieldName = getFieldName(propertyPath);
      final String ciPrefix = ignoreCase ? "(?i)" : "";
      String regexValue;
      if (rightExpression instanceof LiteralExpression literal) {
        Object literalValue = literal.getValue();
        String pattern = literalValue != null ? literalValue.toString() : "";
        if (isLike) {
          pattern = convertLikeToRegex(pattern);
        } else if (startsWith) {
          pattern = "^" + Pattern.quote(pattern) + ".*";
        } else if (endsWith) {
          pattern = ".*" + Pattern.quote(pattern) + "$";
        } else {
          pattern = ".*" + Pattern.quote(pattern) + ".*";
        }
        regexValue = ciPrefix + pattern;
      } else {
        String prefix = startsWith ? "^" : ".*";
        String suffix = endsWith ? "$" : ".*";
        String paramPlaceholder = String.valueOf(valueRepresentation(propertyPath, rightExpression));
        regexValue = ciPrefix + prefix + paramPlaceholder + suffix;
      }
      Map<String, Object> fieldFilter = new LinkedHashMap<>();
      fieldFilter.put(REGEX, regexValue);
      query.put(fieldName, negated ? Map.of(NOT, fieldFilter) : fieldFilter);
    }
  }

  @Nullable
  private Object valueRepresentation(
      final PersistentPropertyPath propertyPath, final jakarta.persistence.criteria.Expression expression) {
    if (expression instanceof LiteralExpression literal) {
      Object value = literal.getValue();
      if (value instanceof RegexPattern regex) {
        return regex.value();
      }
      return value != null ? convertValue(value) : null;
    }
    if (expression instanceof BindingParameter bindingParameter) {
      return bindParameter(bindingParameter, propertyPath);
    }
    
    // Explicitly detect MongoDB-only operators in values/expressions
    String exprString = expression.toString();
    if (exprString.contains("$strLenCP") || exprString.contains("$multiply")) {
        throw new UnsupportedOperationException("NitriteDB does not support MongoDB-only operators: " + exprString);
    }

    return expression;
  }

  @Nullable
  private Object valueRepresentation(
      final PersistentPropertyPath propertyPath,
      final Object value) {
    if (value instanceof LiteralExpression literal) {
      Object val = literal.getValue();
      if (val instanceof RegexPattern regex) {
        return regex.value();
      }
      return val != null ? convertValue(val) : null;
    }
    if (value instanceof BindingParameter bindingParameter) {
      return bindParameter(bindingParameter, propertyPath);
    }
    if (value instanceof jakarta.persistence.criteria.Expression expr) {
      return valueRepresentation(propertyPath, expr);
    }
    return value != null ? convertValue(value) : null;
  }

  private Object bindParameter(
      final BindingParameter bindingParameter,
      final PersistentPropertyPath propertyPath) {
    BindingParameter.BindingContext context = newBindingContext(propertyPath, propertyPath);
    int index = queryState.pushParameter(bindingParameter, context);
    return NitriteQueryBuilder.QUERY_PARAMETER_PLACEHOLDER + ":" + index;
  }

  @Nullable
  private static Object convertValue(@Nullable final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Instant instant) {
      return instant.getEpochSecond() + instant.getNano() / 1_000_000_000.0;
    }
    if (value instanceof LocalDate localDate) {
      return localDate.toString();
    }
    if (value instanceof LocalDateTime localDateTime) {
      return localDateTime.toString();
    }
    if (value instanceof ZonedDateTime zonedDateTime) {
      return zonedDateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
    if (value instanceof ZoneId zoneId) {
      return zoneId.getId();
    }
    return value;
  }

  // -------------------------------------------------------------------------
  // Static utility methods
  // -------------------------------------------------------------------------

  static String getFieldName(final PersistentPropertyPath propertyPath) {
    PersistentProperty property = propertyPath.getProperty();
    PersistentEntity owner = property.getOwner();
    PersistentProperty identity;
    try {
        identity = owner.getIdentity();
    } catch (IllegalStateException e) {
        identity = null;
    }
    if (identity != null && identity.equals(property) && propertyPath.getAssociations().isEmpty()) {
      return ID_FIELD;
    }

    if (propertyPath.getAssociations().isEmpty()) {
      return property.getPersistedName();
    }

    StringBuilder sb = new StringBuilder();
    for (Association association : propertyPath.getAssociations()) {
      if (association.isEmbedded()) {
        sb.append(association.getPersistedName()).append(".");
      } else {
        if (association.getKind() == Relation.Kind.ONE_TO_MANY || association.getKind() == Relation.Kind.MANY_TO_MANY) {
          sb.append(association.getPersistedName()).append(".");
        } else {
          return association.getPersistedName();
        }
      }
    }
    sb.append(property.getPersistedName());
    return sb.toString();
  }

  private static String getFieldNameForNullCheck(
      final Collection<Association> associations, final PersistentProperty property) {
    if (associations.isEmpty()) {
      return property.getPersistedName();
    }
    Association lastAssoc = null;
    for (Association assoc : associations) {
      if (!assoc.isEmbedded()) {
        lastAssoc = assoc;
      }
    }
    if (lastAssoc != null) {
      return lastAssoc.getPersistedName();
    }
    StringBuilder sb = new StringBuilder();
    for (Association association : associations) {
      sb.append(association.getPersistedName()).append(".");
    }
    sb.append(property.getPersistedName());
    return sb.toString();
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

  static String toJsonString(final Object obj) {
    if (obj == null) {
      return "null";
    }
    if (obj instanceof Map map) {
      StringBuilder sb = new StringBuilder("{");
      boolean first = true;
      for (Map.Entry entry : (Set<Map.Entry>) map.entrySet()) {
        if (!first) {
          sb.append(",");
        }
        first = false;
        sb.append(quoteKey(entry.getKey().toString())).append(":");
        sb.append(toJsonString(entry.getValue()));
      }
      sb.append("}");
      return sb.toString();
    }
    if (obj instanceof Collection coll) {
      StringBuilder sb = new StringBuilder("[");
      boolean first = true;
      for (Object item : coll) {
        if (!first) {
          sb.append(",");
        }
        first = false;
        sb.append(toJsonString(item));
      }
      sb.append("]");
      return sb.toString();
    }
    if (obj instanceof String str) {
      StringBuilder sb = new StringBuilder("\"");
      for (int i = 0; i < str.length(); i++) {
        char c = str.charAt(i);
        switch (c) {
          case '\\' -> sb.append("\\\\");
          case '"' -> sb.append("\\\"");
          case '\n' -> sb.append("\\n");
          case '\r' -> sb.append("\\r");
          case '\t' -> sb.append("\\t");
          case '\b' -> sb.append("\\b");
          case '\f' -> sb.append("\\f");
          default -> {
            if (c < 0x20) {
              sb.append(String.format("\\u%04x", (int) c));
            } else {
              sb.append(c);
            }
          }
        }
      }
      sb.append("\"");
      return sb.toString();
    }
    if (obj instanceof Boolean b) {
      return b ? "true" : "false";
    }
    if (obj instanceof Number) {
      return obj.toString();
    }
    return "\"" + obj.toString() + "\"";
  }

  private static String quoteKey(final String key) {
    return "\"" + key + "\"";
  }

  static String asPath(
      final Collection<Association> associations, final PersistentProperty property) {
    if (associations.isEmpty()) {
      return property.getPersistedName();
    }
    StringBuilder sb = new StringBuilder();
    for (Association association : associations) {
      sb.append(association.getPersistedName()).append(".");
    }
    sb.append(property.getPersistedName());
    return sb.toString();
  }

  static String convertLikeToRegex(final String likePattern) {
    StringBuilder regex = new StringBuilder("^");
    for (int i = 0; i < likePattern.length(); i++) {
      char c = likePattern.charAt(i);
      if (c == '%') {
        regex.append(".*");
      } else if (c == '_') {
        regex.append(".");
      } else if ("\\.^$|?*+()[]{}".indexOf(c) >= 0) {
        regex.append("\\").append(c);
      } else {
        regex.append(c);
      }
    }
    regex.append("$");
    return regex.toString();
  }
}
