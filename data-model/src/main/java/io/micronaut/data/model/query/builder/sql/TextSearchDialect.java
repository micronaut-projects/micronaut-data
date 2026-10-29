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
package io.micronaut.data.model.query.builder.sql;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.annotation.search.SearchField;
import io.micronaut.data.annotation.search.SearchWeight;
import io.micronaut.data.annotation.search.Searchable;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.query.builder.QueryParameterBinding;
import io.micronaut.data.model.runtime.convert.OracleTextQueryConverter;
import io.micronaut.data.model.runtime.convert.PostgresTsQueryConverter;
import io.micronaut.data.model.search.MatchMode;
import io.micronaut.data.model.search.SearchCapability;
import io.micronaut.data.model.search.TextSearchSupport;
import io.micronaut.data.model.search.UnsupportedSearchFeatureException;
import jakarta.persistence.criteria.Expression;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Renders full-text search predicates and scores for a SQL dialect.
 *
 * @since 5.4.0
 */
@Internal
public interface TextSearchDialect {

    /**
     * The default text search configuration when the entity declares no {@link Searchable#language()}.
     */
    String DEFAULT_LANGUAGE = TextSearchSupport.DEFAULT_LANGUAGE;

    /**
     * Appends the predicate {@code <fields> match <query>}.
     *
     * @param query The SQL buffer
     * @param fields The matched field expressions
     * @param queryExpression The query parameter expression
     * @param mode The match mode
     * @param target Where the entity data lives
     * @param appendExpression Renders a child expression
     * @param appendParameter Renders the query parameter with the given parameter converter class name
     */
    void appendTextMatch(StringBuilder query,
                         List<Expression<?>> fields,
                         Expression<String> queryExpression,
                         MatchMode mode,
                         Target target,
                         Consumer<Expression<?>> appendExpression,
                         Function<String, Consumer<Expression<?>>> appendParameter);

    /**
     * Appends the relevance score expression for the match.
     *
     * @param query The SQL buffer
     * @param fields The matched field expressions
     * @param queryExpression The query parameter expression
     * @param mode The match mode
     * @param target Where the entity data lives
     * @param appendExpression Renders a child expression
     * @param appendParameter Renders the query parameter with the given parameter converter class name
     */
    void appendSearchScore(StringBuilder query,
                           List<Expression<?>> fields,
                           Expression<String> queryExpression,
                           MatchMode mode,
                           Target target,
                           Consumer<Expression<?>> appendExpression,
                           Function<String, Consumer<Expression<?>>> appendParameter);

    /**
     * Resolves the text search strategy for a SQL dialect.
     *
     * @param dialect The SQL dialect
     * @return The strategy, or {@code null} when the dialect has no full-text search support
     */
    static @Nullable TextSearchDialect forDialect(Dialect dialect) {
        return switch (dialect) {
            case POSTGRES -> PostgresTextSearchDialect.INSTANCE;
            case ORACLE -> OracleTextSearchDialect.INSTANCE;
            default -> null;
        };
    }

    /**
     * Resolves the language of the entity owning the given fields.
     *
     * @param fields The field expressions
     * @return The language, {@link #DEFAULT_LANGUAGE} when unset
     */
    static String resolveLanguage(List<Expression<?>> fields) {
        for (Expression<?> field : fields) {
            PersistentProperty property = asProperty(field);
            if (property != null) {
                return requireLanguageName(TextSearchSupport.resolveLanguage(property.getOwner()));
            }
        }
        return DEFAULT_LANGUAGE;
    }

    /**
     * Checks that a language is a plain text search configuration name, as it is rendered inside the
     * statement rather than bound: a letter followed by letters, digits and underscores.
     *
     * @param language The language
     * @return The language
     * @throws IllegalArgumentException When the language holds any other character
     */
    static String requireLanguageName(String language) {
        if (!language.matches("[A-Za-z][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("A search language must be a text search configuration name of letters, digits and underscores: " + language);
        }
        return language;
    }

    /**
     * @param field A field expression
     * @return The persistent property it refers to, or {@code null}
     */
    static @Nullable PersistentProperty asProperty(Expression<?> field) {
        if (field instanceof io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> propertyPath) {
            return propertyPath.getProperty();
        }
        return null;
    }

    /**
     * @param field A field expression
     * @return The declared {@link SearchField#weight()}, or {@code null} when the property has none
     */
    static @Nullable SearchWeight resolveWeight(Expression<?> field) {
        PersistentProperty property = asProperty(field);
        if (property == null) {
            return null;
        }
        return property.getAnnotationMetadata().enumValue(SearchField.class, SearchField.MEMBER_WEIGHT, SearchWeight.class).orElse(null);
    }

    /**
     * Wraps a parameter binding so the bound value is converted with the given converter.
     *
     * @param binding The original binding
     * @param converterClassName The converter class name
     * @return The wrapped binding
     */
    static QueryParameterBinding withConverter(QueryParameterBinding binding, String converterClassName) {
        return TextSearchSupport.withConverter(binding, converterClassName);
    }

    /**
     * Renders the DDL of the index backing full-text search on the given columns, if the dialect
     * has one.
     *
     * @param indexName The escaped index name
     * @param escapedTableName The escaped table name
     * @param columns The search field columns, escaped when needed
     * @param language The text search language
     * @return The {@code CREATE INDEX} statements, empty when the dialect needs no index
     */
    default List<String> buildSearchIndex(String indexName, String escapedTableName, List<SearchColumn> columns, String language) {
        return List.of();
    }

    /**
     * @param propertyPath A property path of a JSON-represented entity
     * @return The path of its keys in the JSON document, honouring {@code @SerdeConfig(property)} and {@code @JsonProperty}
     */
    static String jsonKeyPath(io.micronaut.data.model.PersistentPropertyPath propertyPath) {
        java.util.StringJoiner path = new java.util.StringJoiner(".");
        for (PersistentProperty association : propertyPath.getAssociations()) {
            path.add(jsonKey(association));
        }
        path.add(jsonKey(propertyPath.getProperty()));
        return path.toString();
    }

    private static String jsonKey(PersistentProperty property) {
        return property.getAnnotationMetadata().stringValue("io.micronaut.serde.config.annotation.SerdeConfig", "property")
            .or(() -> property.getAnnotationMetadata().stringValue("com.fasterxml.jackson.annotation.JsonProperty"))
            .filter(name -> !name.isEmpty())
            .orElse(property.getName());
    }

    /**
     * Where the searched entity lives: a JSON column for JSON-represented entities, else plain columns.
     *
     * @param jsonColumn The JSON column holding the whole entity, or {@code null} for relational entities
     * @param tableAlias The table alias in the query, or {@code null}
     * @param labels The {@code CONTAINS} labels of the statement being built, or {@code null} to start at 1
     */
    record Target(@Nullable String jsonColumn, @Nullable String tableAlias, @Nullable Labels labels) {
        /**
         * A relational target without alias.
         */
        public static final Target RELATIONAL = new Target(null, null, null);

        /**
         * @param jsonColumn The JSON column holding the whole entity, or {@code null} for relational entities
         * @param tableAlias The table alias in the query, or {@code null}
         */
        public Target(@Nullable String jsonColumn, @Nullable String tableAlias) {
            this(jsonColumn, tableAlias, null);
        }

        /**
         * @param labels The labels of the statement being built
         * @return This target with the statement's labels
         */
        public Target withLabels(Labels labels) {
            return new Target(jsonColumn, tableAlias, labels);
        }
    }

    /**
     * The {@code CONTAINS} labels of one statement. Each text match takes its own range, and its score,
     * rendered in the select list and the order, reads the same labels: a match is known by its fields
     * and its query parameter, as each clause holds its own copy of the expressions.
     */
    final class Labels {
        private final java.util.Map<String, Integer> bases = new java.util.HashMap<>();
        private int next = 1;

        /**
         * @param fields The fields of the text match
         * @param queryExpression The query expression of the text match
         * @param count The number of labels the match uses
         * @return The first label of the match
         */
        public int base(List<Expression<?>> fields, Expression<?> queryExpression, int count) {
            return bases.computeIfAbsent(key(fields, queryExpression), k -> {
                int base = next;
                next += count;
                return base;
            });
        }

        private static String key(List<Expression<?>> fields, Expression<?> queryExpression) {
            java.util.StringJoiner key = new java.util.StringJoiner(",", "", "|");
            for (Expression<?> field : fields) {
                key.add(field instanceof io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> path
                    ? path.getPropertyPath().getPath()
                    : String.valueOf(field));
            }
            String parameter = queryExpression instanceof jakarta.persistence.criteria.ParameterExpression<?> p && p.getName() != null
                ? p.getName()
                : "";
            return key + parameter;
        }
    }

    /**
     * A search field column for DDL rendering.
     *
     * @param column The column name, escaped when needed
     * @param weight The declared weight, or {@code null}
     */
    record SearchColumn(String column, @Nullable SearchWeight weight) {
    }

    /**
     * PostgreSQL {@code tsvector}/{@code tsquery} renderer.
     *
     * <p>Fields are combined into one {@code tsvector}; when a field declares a {@link SearchField#weight()}
     * it is wrapped in {@code setweight}. The query string is converted at bind time by a
     * {@link PostgresTsQueryConverter} into {@code tsquery} syntax for the match mode, so the SQL
     * is always {@code to_tsquery(config, ?)}.</p>
     */
    enum PostgresTextSearchDialect implements TextSearchDialect {
        INSTANCE;

        @Override
        public void appendTextMatch(StringBuilder query,
                                    List<Expression<?>> fields,
                                    Expression<String> queryExpression,
                                    MatchMode mode,
                                    Target target,
                                    Consumer<Expression<?>> appendExpression,
                                    Function<String, Consumer<Expression<?>>> appendParameter) {
            if (mode == MatchMode.FUZZY) {
                throw new UnsupportedSearchFeatureException(SearchCapability.FUZZY, "PostgreSQL text search");
            }
            String language = resolveLanguage(fields);
            appendVector(query, fields, language, appendExpression);
            query.append(" @@ ");
            appendTsQuery(query, queryExpression, mode, language, appendParameter);
        }

        @Override
        public void appendSearchScore(StringBuilder query,
                                      List<Expression<?>> fields,
                                      Expression<String> queryExpression,
                                      MatchMode mode,
                                      Target target,
                                      Consumer<Expression<?>> appendExpression,
                                      Function<String, Consumer<Expression<?>>> appendParameter) {
            String language = resolveLanguage(fields);
            query.append("ts_rank_cd(");
            appendVector(query, fields, language, appendExpression);
            query.append(',');
            appendTsQuery(query, queryExpression, mode, language, appendParameter);
            query.append(')');
        }

        private void appendTsQuery(StringBuilder query,
                                   Expression<String> queryExpression,
                                   MatchMode mode,
                                   String language,
                                   Function<String, Consumer<Expression<?>>> appendParameter) {
            query.append("to_tsquery('").append(language).append("',");
            appendParameter.apply(PostgresTsQueryConverter.converterFor(mode)).accept(queryExpression);
            query.append(')');
        }

        @Override
        public List<String> buildSearchIndex(String indexName, String escapedTableName, List<SearchColumn> columns, String language) {
            StringBuilder ddl = new StringBuilder("CREATE INDEX IF NOT EXISTS ").append(indexName).append(" ON ").append(escapedTableName).append(" USING GIN (");
            List<SearchWeight> weights = columns.stream().map(SearchColumn::weight).toList();
            List<Consumer<StringBuilder>> renderers = columns.stream().map(c -> (Consumer<StringBuilder>) sb -> sb.append(c.column())).toList();
            appendVector(ddl, renderers, weights, language);
            return List.of(ddl.append(");").toString());
        }

        private void appendVector(StringBuilder query,
                                  List<Expression<?>> fields,
                                  String language,
                                  Consumer<Expression<?>> appendExpression) {
            List<SearchWeight> weights = fields.stream().map(TextSearchDialect::resolveWeight).toList();
            List<Consumer<StringBuilder>> renderers = fields.stream().map(f -> (Consumer<StringBuilder>) sb -> appendExpression.accept(f)).toList();
            appendVector(query, renderers, weights, language);
        }

        /**
         * Renders the combined {@code tsvector} of the fields; the same shape is used by the query and
         * by the expression index so PostgreSQL can match them.
         */
        private static void appendVector(StringBuilder query,
                                         List<Consumer<StringBuilder>> fieldRenderers,
                                         List<SearchWeight> weights,
                                         String language) {
            boolean multiple = fieldRenderers.size() > 1;
            if (multiple) {
                query.append('(');
            }
            for (int i = 0; i < fieldRenderers.size(); i++) {
                if (i > 0) {
                    query.append(" || ");
                }
                SearchWeight weight = weights.get(i);
                if (weight != null) {
                    query.append("setweight(");
                }
                query.append("to_tsvector('").append(language).append("',");
                if (multiple) {
                    query.append("coalesce(");
                    fieldRenderers.get(i).accept(query);
                    query.append(",''))");
                } else {
                    fieldRenderers.get(i).accept(query);
                    query.append(')');
                }
                if (weight != null) {
                    query.append(",'").append(weight.postgresLabel()).append("')");
                }
            }
            if (multiple) {
                query.append(')');
            }
        }
    }

    /**
     * Oracle Text renderer: one {@code CONTEXT} index per search field, {@code CONTAINS(col, ?, n) > 0}
     * per field and {@code SCORE(n)} for the relevance, weighted by {@link SearchField#weight()}.
     *
     * <p>A search over several fields matches any field with the positive terms and then excludes rows
     * where any field matches a negated term. JSON-represented entities use
     * {@code JSON_TEXTCONTAINS(column, '$.path', ?, n)} instead.</p>
     */
    enum OracleTextSearchDialect implements TextSearchDialect {
        INSTANCE;

        private static final String CONTAINS_SUFFIX = ") > 0";

        @Override
        public void appendTextMatch(StringBuilder query,
                                    List<Expression<?>> fields,
                                    Expression<String> queryExpression,
                                    MatchMode mode,
                                    Target target,
                                    Consumer<Expression<?>> appendExpression,
                                    Function<String, Consumer<Expression<?>>> appendParameter) {
            int base = firstLabel(fields, queryExpression, target);
            if (fields.size() == 1) {
                appendContains(query, fields.get(0), base, queryExpression, target, appendExpression,
                    appendParameter.apply(OracleTextQueryConverter.converterFor(mode, OracleTextQueryConverter.Part.FULL)));
                return;
            }
            query.append("((");
            Consumer<Expression<?>> positive = appendParameter.apply(OracleTextQueryConverter.converterFor(mode, OracleTextQueryConverter.Part.POSITIVE));
            appendAnyField(query, fields, base, queryExpression, target, appendExpression, positive);
            query.append(") AND NOT (");
            Consumer<Expression<?>> negated = appendParameter.apply(OracleTextQueryConverter.converterFor(mode, OracleTextQueryConverter.Part.NEGATED));
            appendAnyField(query, fields, base + fields.size(), queryExpression, target, appendExpression, negated);
            query.append("))");
        }

        @Override
        public void appendSearchScore(StringBuilder query,
                                      List<Expression<?>> fields,
                                      Expression<String> queryExpression,
                                      MatchMode mode,
                                      Target target,
                                      Consumer<Expression<?>> appendExpression,
                                      Function<String, Consumer<Expression<?>>> appendParameter) {
            int base = firstLabel(fields, queryExpression, target);
            if (fields.size() == 1) {
                query.append("SCORE(").append(base).append(')');
                return;
            }
            boolean weighted = fields.stream().anyMatch(f -> resolveWeight(f) != null);
            query.append('(');
            for (int i = 0; i < fields.size(); i++) {
                if (i > 0) {
                    query.append(" + ");
                }
                query.append("SCORE(").append(base + i).append(')');
                if (weighted) {
                    SearchWeight weight = resolveWeight(fields.get(i));
                    query.append(" * ").append(weight == null ? 1 : weight.numericWeight());
                }
            }
            query.append(')');
        }

        @Override
        public List<String> buildSearchIndex(String indexName, String escapedTableName, List<SearchColumn> columns, String language) {
            List<String> statements = new java.util.ArrayList<>(columns.size());
            for (SearchColumn column : columns) {
                String name = indexName.endsWith("\"")
                    ? indexName.substring(0, indexName.length() - 1) + "_" + column.column().replace("\"", "") + "\""
                    : indexName + "_" + column.column();
                statements.add("CREATE INDEX " + name + " ON " + escapedTableName + " (" + column.column()
                    + ") INDEXTYPE IS CTXSYS.CONTEXT PARAMETERS ('SYNC (ON COMMIT)');");
            }
            return statements;
        }

        /**
         * The first label of a match: from the statement's labels when the builder passes them, so two
         * matches in one statement never share a label, else 1. A single field takes one label; several
         * fields take one per field for the positive terms and one per field for the negated ones.
         */
        private static int firstLabel(List<Expression<?>> fields, Expression<String> queryExpression, Target target) {
            Labels labels = target.labels();
            return labels == null ? 1 : labels.base(fields, queryExpression, fields.size() == 1 ? 1 : 2 * fields.size());
        }

        private void appendAnyField(StringBuilder query,
                                    List<Expression<?>> fields,
                                    int firstLabel,
                                    Expression<String> queryExpression,
                                    Target target,
                                    Consumer<Expression<?>> appendExpression,
                                    Consumer<Expression<?>> appendQuery) {
            for (int i = 0; i < fields.size(); i++) {
                if (i > 0) {
                    query.append(" OR ");
                }
                appendContains(query, fields.get(i), firstLabel + i, queryExpression, target, appendExpression, appendQuery);
            }
        }

        private void appendContains(StringBuilder query,
                                    Expression<?> field,
                                    int label,
                                    Expression<String> queryExpression,
                                    Target target,
                                    Consumer<Expression<?>> appendExpression,
                                    Consumer<Expression<?>> appendQuery) {
            if (target.jsonColumn() != null && field instanceof io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> propertyPath) {
                query.append("JSON_TEXTCONTAINS(");
                if (target.tableAlias() != null) {
                    query.append(target.tableAlias()).append('.');
                }
                query.append(target.jsonColumn()).append(",'$.").append(TextSearchDialect.jsonKeyPath(propertyPath.getPropertyPath())).append("',");
                appendQuery.accept(queryExpression);
                query.append(',').append(label).append(')');
                return;
            }
            query.append("CONTAINS(");
            appendExpression.accept(field);
            query.append(',');
            appendQuery.accept(queryExpression);
            query.append(',').append(label).append(CONTAINS_SUFFIX);
        }
    }
}
