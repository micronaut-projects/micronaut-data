/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.data.search.document.query;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.annotation.search.SearchField;
import io.micronaut.data.annotation.search.SearchFieldType;
import io.micronaut.data.annotation.search.SearchWeight;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.IExpression;
import io.micronaut.data.model.jpa.criteria.IPredicate;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils;
import io.micronaut.data.model.jpa.criteria.impl.expression.FunctionExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.LiteralExpression;
import io.micronaut.data.model.jpa.criteria.impl.predicate.ConjunctionPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.DisjunctionPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.ExistsSubqueryPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.LikePredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.NegatedPredicate;
import io.micronaut.data.model.jpa.criteria.impl.selection.AliasedSelection;
import io.micronaut.data.model.jpa.criteria.impl.selection.CompoundSelection;
import io.micronaut.data.model.query.BindingParameter;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryParameterBinding;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.model.query.builder.sql.VectorScoringDialectSupport;
import io.micronaut.data.model.query.impl.AdvancedPredicateVisitor;
import io.micronaut.data.model.search.MatchMode;
import io.micronaut.data.model.search.SearchCapability;
import io.micronaut.data.model.search.TextMatchOptions;
import io.micronaut.data.model.search.UnsupportedSearchFeatureException;
import io.micronaut.data.model.search.TextSearchSupport;
import io.micronaut.data.search.document.convert.SimpleQueryStringConverter;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Selection;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds the JSON request body of an Elasticsearch or OpenSearch search from the criteria tree.
 *
 * <p>The body holds {@code query}, {@code sort}, {@code from} and {@code size}. Method parameters are
 * written as {@code {"$mn_qp": n}} objects that the runtime replaces with the bound values; wildcard
 * matches carry a {@code "$mn_wildcard"} hint so the runtime can build the pattern. A full-text match
 * renders a {@code simple_query_string} over the search fields, boosted by their weights, with the
 * search string converted to Lucene syntax at bind time.</p>
 *
 * @since 5.4.0
 */
@Internal
@Introspected
public final class DocumentSearchQueryBuilder implements QueryBuilder {

    /**
     * The key of a parameter placeholder object.
     */
    public static final String QUERY_PARAMETER_PLACEHOLDER = "$mn_qp";

    /**
     * The key carrying how a bound value is turned into a wildcard pattern.
     */
    public static final String WILDCARD_HINT = "$mn_wildcard";

    /**
     * The field name Elasticsearch and OpenSearch use for the document id.
     */
    public static final String ID_FIELD = "_id";

    /**
     * The field name of the relevance score in sorts.
     */
    public static final String SCORE_FIELD = "_score";

    /**
     * The wildcard hint turning a bound cosine distance into the store's similarity threshold.
     */
    public static final String COSINE_SIMILARITY_HINT = "cosine_similarity";

    private static final int DEFAULT_K = 10;
    private static final int DEFAULT_NUM_CANDIDATES = 100;

    private static final String KEYWORD_SUFFIX = ".keyword";
    private static final String QUERY = "query";
    private static final Map<String, Object> MATCH_ALL = Map.of("match_all", Map.of());
    private static final String BOOL = "bool";
    private static final String MUST = "must";
    private static final String MUST_NOT = "must_not";
    private static final String SHOULD = "should";
    private static final String TERM = "term";
    private static final String VALUE = "value";
    private static final String FIELD = "field";
    private static final String RANGE = "range";
    private static final String ORDER = "order";

    @Override
    @SuppressWarnings("NullAway")
    public QueryResult buildInsert(AnnotationMetadata repositoryMetadata, InsertQueryDefinition definition) {
        // Entities are indexed through the repository operations, not through a stored insert query
        return null;
    }

    @Override
    public QueryResult buildSelect(AnnotationMetadata annotationMetadata, SelectQueryDefinition definition) {
        PersistentEntity entity = definition.persistentEntity();
        Bindings bindings = new Bindings();
        Map<String, Object> body = new LinkedHashMap<>();
        DocumentPredicateVisitor visitor = new DocumentPredicateVisitor(entity, bindings, definition.limit());
        body.put(QUERY, queryOf(definition, visitor));
        if (visitor.minScore != null) {
            body.put("min_score", visitor.minScore);
        }
        addHighlight(definition, body);
        List<Map<String, Object>> sort = new ArrayList<>();
        for (Order order : definition.order()) {
            sort.add(sortEntry(order));
        }
        if (!sort.isEmpty()) {
            body.put("sort", sort);
        }
        if (definition.offset() > 0) {
            body.put("from", definition.offset());
        }
        if (definition.limit() != -1) {
            body.put("size", definition.limit());
        }
        return QueryResult.of(SearchJson.write(body), bindings.list);
    }

    @Override
    public QueryResult buildUpdate(AnnotationMetadata annotationMetadata, UpdateQueryDefinition definition) {
        // Entity updates re-index the whole document at runtime; the query only identifies the documents
        Bindings bindings = new Bindings();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(QUERY, buildQuery(definition.predicate(), definition.persistentEntity(), bindings));
        return QueryResult.of(SearchJson.write(body), bindings.list);
    }

    @Override
    public QueryResult buildDelete(AnnotationMetadata annotationMetadata, DeleteQueryDefinition definition) {
        Bindings bindings = new Bindings();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(QUERY, buildQuery(definition.predicate(), definition.persistentEntity(), bindings));
        return QueryResult.of(SearchJson.write(body), bindings.list);
    }

    @Override
    public String buildLimitAndOffset(long limit, long offset) {
        return "";
    }

    /**
     * Finds the {@link TextSearchSupport#HIGHLIGHT_FUNCTION} projections of a selection and registers their
     * fields; the fragments come back in each hit's {@code highlight} with the default {@code &lt;em&gt;} tags.
     */
    private static void collectHighlightFields(Selection<?> selection, Map<String, Object> highlightFields) {
        if (selection instanceof AliasedSelection<?> aliased) {
            collectHighlightFields(aliased.getSelection(), highlightFields);
            return;
        }
        if (selection instanceof CompoundSelection<?> compound) {
            for (Selection<?> item : compound.getCompoundSelectionItems()) {
                collectHighlightFields(item, highlightFields);
            }
            return;
        }
        if (selection instanceof FunctionExpression<?> function && TextSearchSupport.HIGHLIGHT_FUNCTION.equals(function.getName())) {
            Expression<?> field = function.getExpressions().get(0);
            if (!(field instanceof io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> propertyPath)) {
                throw new IllegalStateException("A highlighted field must be a property, got: " + field);
            }
            highlightFields.put(fieldName(propertyPath.getPropertyPath()), Map.of());
        }
    }

    /** The predicate's query, or a nearest-neighbour {@code knn} when only a vector ordering asks for one. */
    private static Map<String, Object> queryOf(SelectQueryDefinition definition, DocumentPredicateVisitor visitor) {
        Map<String, Object> query = definition.predicate() == null ? MATCH_ALL : visitor.build(definition.predicate());
        if (!visitor.knnFields.isEmpty()) {
            return query;
        }
        // a vector ordering without a vector restriction is a nearest-neighbour search
        for (Order order : definition.order()) {
            if (order.getExpression() instanceof FunctionExpression<?> function && VectorScoringDialectSupport.SCORE_FUNCTION.equals(function.getName())) {
                return visitor.knn(function, null);
            }
        }
        return query;
    }

    private static void addHighlight(SelectQueryDefinition definition, Map<String, Object> body) {
        Map<String, Object> highlightFields = new LinkedHashMap<>();
        if (definition.selection() != null) {
            collectHighlightFields(definition.selection(), highlightFields);
        }
        if (highlightFields.isEmpty()) {
            return;
        }
        // One object per field, the ordered form every Elasticsearch client version accepts
        List<Map<String, Object>> fields = new ArrayList<>(highlightFields.size());
        for (Map.Entry<String, Object> field : highlightFields.entrySet()) {
            fields.add(Map.of(field.getKey(), field.getValue()));
        }
        body.put("highlight", Map.of("fields", fields));
    }

    private Map<String, Object> buildQuery(@Nullable Predicate predicate, PersistentEntity entity, Bindings bindings) {
        if (predicate == null) {
            return MATCH_ALL;
        }
        return new DocumentPredicateVisitor(entity, bindings, -1).build(predicate);
    }

    private Map<String, Object> sortEntry(Order order) {
        Expression<?> expression = order.getExpression();
        String field;
        if (expression instanceof io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> propertyPath) {
            field = keywordPath(propertyPath.getPropertyPath());
        } else if (expression instanceof FunctionExpression<?> function && TextSearchSupport.SCORE_FUNCTION.equals(function.getName())) {
            field = SCORE_FIELD;
        } else if (expression instanceof FunctionExpression<?> function && VectorScoringDialectSupport.SCORE_FUNCTION.equals(function.getName())) {
            // the nearest neighbours score highest, whatever the direction the distance ordering asked for
            return Map.of(SCORE_FIELD, Map.of(ORDER, "desc"));
        } else {
            throw new UnsupportedOperationException("Ordering by expression " + expression + " is not supported by document search stores");
        }
        return Map.of(field, Map.of(ORDER, order.isAscending() ? "asc" : "desc"));
    }

    /**
     * The persisted field name of a property path.
     *
     * @param propertyPath The property path
     * @return The field name
     */
    public static String fieldName(PersistentPropertyPath propertyPath) {
        return propertyPath.getPath();
    }

    /**
     * The field used for exact matches and sorting: the {@code .keyword} sub-field of an analysed
     * search field, the field itself otherwise.
     *
     * @param propertyPath The property path
     * @return The field name
     */
    public static String keywordPath(PersistentPropertyPath propertyPath) {
        return isAnalysed(propertyPath.getProperty()) ? fieldName(propertyPath) + KEYWORD_SUFFIX : fieldName(propertyPath);
    }

    /**
     * @param property The property
     * @return Whether the property is an analysed text search field
     */
    public static boolean isAnalysed(PersistentProperty property) {
        SearchFieldType type = property.getAnnotationMetadata().enumValue(SearchField.class, SearchField.MEMBER_TYPE, SearchFieldType.class).orElse(null);
        if (type == null) {
            return property.getAnnotationMetadata().hasAnnotation(SearchField.class);
        }
        return type != SearchFieldType.KEYWORD;
    }

    private static final class Bindings {
        private final List<QueryParameterBinding> list = new ArrayList<>();

        Map<String, Object> placeholder(BindingParameter parameter, @Nullable PersistentPropertyPath propertyPath, @Nullable String wildcard) {
            int index = list.size();
            BindingParameter.BindingContext context = BindingParameter.BindingContext.create()
                .index(index)
                .incomingMethodParameterProperty(propertyPath)
                .outgoingQueryParameterProperty(propertyPath);
            list.add(parameter.bind(context));
            Map<String, Object> placeholder = new LinkedHashMap<>();
            placeholder.put(QUERY_PARAMETER_PLACEHOLDER, index);
            if (wildcard != null) {
                placeholder.put(WILDCARD_HINT, wildcard);
            }
            return placeholder;
        }

        Map<String, Object> placeholder(BindingParameter parameter, String converterClassName) {
            int index = list.size();
            BindingParameter.BindingContext context = BindingParameter.BindingContext.create().index(index);
            list.add(TextSearchSupport.withConverter(parameter.bind(context), converterClassName));
            return Map.of(QUERY_PARAMETER_PLACEHOLDER, index);
        }
    }

    private static final class DocumentPredicateVisitor implements AdvancedPredicateVisitor<PersistentPropertyPath> {

        private final PersistentEntity entity;
        private final Bindings bindings;
        private final int limit;
        private final List<String> knnFields = new ArrayList<>();
        private Map<String, Object> result = Map.of();
        @Nullable
        private Object minScore;

        DocumentPredicateVisitor(PersistentEntity entity, Bindings bindings, int limit) {
            this.entity = entity;
            this.bindings = bindings;
            this.limit = limit;
        }

        /**
         * The {@code knn} query of a {@code mn_vector_score(field, vector)} function, in the Elasticsearch
         * shape; the OpenSearch transport translates it. {@code k} follows the requested size and the
         * optional maximum cosine distance becomes the store's similarity threshold.
         */
        Map<String, Object> knn(FunctionExpression<?> function, @Nullable Expression<?> maxDistance) {
            PersistentPropertyPath propertyPath = requireProperty(function.getExpressions().get(0)).getPropertyPath();
            Expression<?> vector = function.getExpressions().get(1);
            Map<String, Object> knn = new LinkedHashMap<>();
            knn.put(FIELD, fieldName(propertyPath));
            knn.put("query_vector", value(vector, propertyPath));
            int k = limit > 0 ? limit : DEFAULT_K;
            knn.put("k", k);
            knn.put("num_candidates", Math.max(k * 10, DEFAULT_NUM_CANDIDATES));
            if (maxDistance != null) {
                knn.put("similarity", distanceToSimilarity(maxDistance));
            }
            knnFields.add(fieldName(propertyPath));
            return Map.of("knn", knn);
        }

        private Object distanceToSimilarity(Expression<?> maxDistance) {
            if (maxDistance instanceof BindingParameter parameter) {
                return bindings.placeholder(parameter, null, COSINE_SIMILARITY_HINT);
            }
            Object literal = Objects.requireNonNull(literal(maxDistance), "The maximum distance must be a parameter or a literal");
            return literal instanceof Number number ? 1d - number.doubleValue() : literal;
        }

        Map<String, Object> build(Predicate predicate) {
            visitPredicate(predicate);
            return result;
        }

        @Override
        public PersistentPropertyPath getRequiredProperty(io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> persistentPropertyPath) {
            return persistentPropertyPath.getPropertyPath();
        }

        private void visitPredicate(Expression<?> expression) {
            if (expression instanceof IPredicate predicate) {
                predicate.visitPredicate(this);
            } else if (expression instanceof io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?>) {
                visitIsTrue(expression);
            } else {
                throw new IllegalStateException("Unknown boolean expression: " + expression);
            }
        }

        private Map<String, Object> render(Expression<?> expression) {
            visitPredicate(expression);
            return result;
        }

        @Override
        public void visit(ConjunctionPredicate conjunction) {
            List<Map<String, Object>> must = new ArrayList<>();
            for (IExpression<Boolean> predicate : conjunction.getPredicates()) {
                must.add(render(predicate));
            }
            result = must.size() == 1 ? must.get(0) : Map.of(BOOL, Map.of(MUST, must));
        }

        @Override
        public void visit(DisjunctionPredicate disjunction) {
            List<Map<String, Object>> should = new ArrayList<>();
            for (IExpression<Boolean> predicate : disjunction.getPredicates()) {
                should.add(render(predicate));
            }
            if (should.size() == 1) {
                result = should.get(0);
                return;
            }
            Map<String, Object> bool = new LinkedHashMap<>();
            bool.put(SHOULD, should);
            bool.put("minimum_should_match", 1);
            result = Map.of(BOOL, bool);
        }

        @Override
        public void visit(NegatedPredicate negate) {
            result = Map.of(BOOL, Map.of(MUST_NOT, render(negate.getNegated())));
        }

        @Override
        public void visit(LikePredicate likePredicate) {
            PersistentPropertyPath propertyPath = requireProperty(likePredicate.getExpression()).getPropertyPath();
            result = wildcard(propertyPath, likePredicate.getPattern(), "like");
        }

        @Override
        public void visit(ExistsSubqueryPredicate existsSubqueryPredicate) {
            throw new UnsupportedOperationException("Subqueries are not supported by document search stores");
        }

        @Override
        public void visitIdEquals(Expression<?> expression) {
            result = Map.of("ids", Map.of("values", List.of(requireValue(value(expression, null), expression))));
        }

        @Override
        public void visitEquals(Expression<?> leftExpression, Expression<?> rightExpression, boolean ignoreCase) {
            PersistentPropertyPath propertyPath = requireProperty(leftExpression).getPropertyPath();
            if (isIdentity(propertyPath)) {
                visitIdEquals(rightExpression);
                return;
            }
            if (ignoreCase) {
                result = Map.of(TERM, Map.of(keywordPath(propertyPath), Map.of(VALUE, value(rightExpression, propertyPath), "case_insensitive", true)));
                return;
            }
            result = Map.of(TERM, Map.of(keywordPath(propertyPath), value(rightExpression, propertyPath)));
        }

        @Override
        public void visitNotEquals(Expression<?> leftExpression, Expression<?> rightExpression, boolean ignoreCase) {
            visitEquals(leftExpression, rightExpression, ignoreCase);
            result = Map.of(BOOL, Map.of(MUST_NOT, result));
        }

        @Override
        public void visitGreaterThan(Expression<?> leftExpression, Expression<?> rightExpression) {
            result = range(leftExpression, "gt", rightExpression);
        }

        @Override
        public void visitGreaterThanOrEquals(Expression<?> leftExpression, Expression<?> rightExpression) {
            if (leftExpression instanceof FunctionExpression<?> function && TextSearchSupport.SCORE_FUNCTION.equals(function.getName())) {
                // a minimum relevance score is a request parameter, not a query clause
                minScore = value(rightExpression, null);
                result = MATCH_ALL;
                return;
            }
            result = range(leftExpression, "gte", rightExpression);
        }

        @Override
        public void visitLessThan(Expression<?> leftExpression, Expression<?> rightExpression) {
            result = range(leftExpression, "lt", rightExpression);
        }

        @Override
        public void visitLessThanOrEquals(Expression<?> leftExpression, Expression<?> rightExpression) {
            if (leftExpression instanceof FunctionExpression<?> function && VectorScoringDialectSupport.SCORE_FUNCTION.equals(function.getName())) {
                result = knn(function, rightExpression);
                return;
            }
            result = range(leftExpression, "lte", rightExpression);
        }

        @Override
        public void visitInBetween(Expression<?> value, Expression<?> from, Expression<?> to, boolean negated) {
            PersistentPropertyPath propertyPath = requireProperty(value).getPropertyPath();
            Map<String, Object> bounds = new LinkedHashMap<>();
            bounds.put("gte", value(from, propertyPath));
            bounds.put("lte", value(to, propertyPath));
            result = Map.of(RANGE, Map.of(fieldName(propertyPath), bounds));
            if (negated) {
                result = Map.of(BOOL, Map.of(MUST_NOT, result));
            }
        }

        @Override
        public void visitStartsWith(Expression<?> leftExpression, Expression<?> rightExpression, boolean ignoreCase) {
            result = wildcard(requireProperty(leftExpression).getPropertyPath(), rightExpression, ignoreCase ? "starts_with_ignore_case" : "starts_with");
        }

        @Override
        public void visitEndsWith(Expression<?> leftExpression, Expression<?> expression, boolean ignoreCase) {
            result = wildcard(requireProperty(leftExpression).getPropertyPath(), expression, ignoreCase ? "ends_with_ignore_case" : "ends_with");
        }

        @Override
        public void visitContains(Expression<?> leftExpression, Expression<?> expression, boolean ignoreCase) {
            result = wildcard(requireProperty(leftExpression).getPropertyPath(), expression, ignoreCase ? "contains_ignore_case" : "contains");
        }

        @Override
        public void visitRegexp(Expression<?> leftExpression, Expression<?> expression) {
            PersistentPropertyPath propertyPath = requireProperty(leftExpression).getPropertyPath();
            result = Map.of("regexp", Map.of(keywordPath(propertyPath), value(expression, propertyPath)));
        }

        @Override
        public void visitIsFalse(Expression<?> expression) {
            result = Map.of(TERM, Map.of(fieldName(requireProperty(expression).getPropertyPath()), false));
        }

        @Override
        public void visitIsTrue(Expression<?> expression) {
            result = Map.of(TERM, Map.of(fieldName(requireProperty(expression).getPropertyPath()), true));
        }

        @Override
        public void visitIsNotNull(Expression<?> expression) {
            result = Map.of("exists", Map.of(FIELD, fieldName(requireProperty(expression).getPropertyPath())));
        }

        @Override
        public void visitIsNull(Expression<?> expression) {
            visitIsNotNull(expression);
            result = Map.of(BOOL, Map.of(MUST_NOT, result));
        }

        @Override
        public void visitIsEmpty(Expression<?> expression) {
            PersistentPropertyPath propertyPath = requireProperty(expression).getPropertyPath();
            result = Map.of(BOOL, Map.of(SHOULD, List.of(
                Map.of(BOOL, Map.of(MUST_NOT, Map.of("exists", Map.of(FIELD, fieldName(propertyPath))))),
                Map.of(TERM, Map.of(keywordPath(propertyPath), ""))
            ), "minimum_should_match", 1));
        }

        @Override
        public void visitIsNotEmpty(Expression<?> expression) {
            visitIsEmpty(expression);
            result = Map.of(BOOL, Map.of(MUST_NOT, result));
        }

        @Override
        public void visitIn(Expression<?> expression, Collection<?> values, boolean negated) {
            PersistentPropertyPath propertyPath = requireProperty(expression).getPropertyPath();
            List<Object> rendered = new ArrayList<>(values.size());
            for (Object value : values) {
                rendered.add(value instanceof Expression<?> e ? value(e, propertyPath) : value);
            }
            result = isIdentity(propertyPath)
                ? Map.of("ids", Map.of("values", rendered))
                : Map.of("terms", Map.of(keywordPath(propertyPath), rendered));
            if (negated) {
                result = Map.of(BOOL, Map.of(MUST_NOT, result));
            }
        }

        @Override
        public void visitTextMatch(List<Expression<?>> fields, Expression<String> query, MatchMode mode) {
            visitTextMatch(fields, query, mode, TextMatchOptions.NONE);
        }

        @Override
        public void visitTextMatch(List<Expression<?>> fields, Expression<String> query, MatchMode mode, TextMatchOptions options) {
            if (!(query instanceof BindingParameter parameter)) {
                throw new IllegalStateException("A full-text search query must be a parameter, got: " + query);
            }
            if (options.fuzziness() > SimpleQueryStringConverter.MAX_FUZZINESS) {
                throw new UnsupportedSearchFeatureException(SearchCapability.FUZZINESS, "Lucene fuzzy matching beyond " + SimpleQueryStringConverter.MAX_FUZZINESS + " edits");
            }
            List<String> fieldNames = new ArrayList<>(fields.size());
            for (Expression<?> field : fields) {
                PersistentPropertyPath propertyPath = requireProperty(field).getPropertyPath();
                // a query-time boost replaces the index-time weight of the field
                Double boost = options.boostFor(propertyPath.getProperty().getName());
                SearchWeight weight = propertyPath.getProperty().getAnnotationMetadata().enumValue(SearchField.class, SearchField.MEMBER_WEIGHT, SearchWeight.class).orElse(null);
                if (boost != null) {
                    fieldNames.add(fieldName(propertyPath) + "^" + boost);
                } else {
                    fieldNames.add(weight == null ? fieldName(propertyPath) : fieldName(propertyPath) + "^" + weight.numericWeight());
                }
            }
            Map<String, Object> simpleQueryString = new LinkedHashMap<>();
            simpleQueryString.put(QUERY, bindings.placeholder(parameter, SimpleQueryStringConverter.converterFor(mode, options.fuzziness())));
            simpleQueryString.put("fields", fieldNames);
            // Always "and": the converter spells any-term matching out with "|", so a negated term
            // excludes rather than matching every document without it
            simpleQueryString.put("default_operator", "and");
            if (mode == MatchMode.PREFIX) {
                // Analyze the prefix with the field analyzer, so a stemmed term such as "programming*" still matches
                simpleQueryString.put("analyze_wildcard", true);
            }
            result = Map.of("simple_query_string", simpleQueryString);
        }

        private Map<String, Object> range(Expression<?> leftExpression, String operator, Expression<?> rightExpression) {
            PersistentPropertyPath propertyPath = requireProperty(leftExpression).getPropertyPath();
            return Map.of(RANGE, Map.of(fieldName(propertyPath), Map.of(operator, value(rightExpression, propertyPath))));
        }

        private Map<String, Object> wildcard(PersistentPropertyPath propertyPath, Expression<?> expression, String hint) {
            Object value;
            if (expression instanceof BindingParameter parameter) {
                value = bindings.placeholder(parameter, propertyPath, hint);
            } else {
                value = Map.of(QUERY_PARAMETER_PLACEHOLDER, -1, WILDCARD_HINT, hint, VALUE, requireValue(literal(expression), expression));
            }
            Map<String, Object> options = new LinkedHashMap<>();
            options.put(VALUE, value);
            if (hint.endsWith("_ignore_case")) {
                options.put("case_insensitive", true);
            }
            return Map.of("wildcard", Map.of(keywordPath(propertyPath), options));
        }

        @Nullable
        private Object value(Expression<?> expression, @Nullable PersistentPropertyPath propertyPath) {
            if (expression instanceof BindingParameter parameter) {
                return bindings.placeholder(parameter, propertyPath, null);
            }
            return literal(expression);
        }

        /** A literal that the query cannot express as null, such as an id or a pattern. */
        private static Object requireValue(@Nullable Object value, Expression<?> expression) {
            if (value == null) {
                throw new IllegalArgumentException("A null literal is not supported here by document search stores: " + expression + "; use an IS NULL predicate");
            }
            return value;
        }

        @Nullable
        private Object literal(Expression<?> expression) {
            if (expression instanceof LiteralExpression<?> literal) {
                return literal.getValue();
            }
            throw new UnsupportedOperationException("Expression " + expression + " is not supported by document search stores");
        }

        private boolean isIdentity(PersistentPropertyPath propertyPath) {
            return propertyPath.getAssociations().isEmpty() && entity.hasIdentity() && entity.getIdentity() == propertyPath.getProperty();
        }

        private io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> requireProperty(Expression<?> expression) {
            if (expression instanceof io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> propertyPath) {
                return propertyPath;
            }
            if (expression instanceof PersistentEntityRoot<?> root) {
                return CriteriaUtils.requireProperty(root.id());
            }
            throw new IllegalStateException("Expected a property expression, got: " + expression);
        }
    }
}
