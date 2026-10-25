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
import io.micronaut.data.model.search.TextMatchOptions;
import io.micronaut.data.model.search.TextSearchSupport;
import io.micronaut.data.model.search.UnsupportedSearchFeatureException;
import jakarta.persistence.criteria.Expression;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
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
     * The alias of the unnested fuzzy terms.
     */
    String TERM_ALIAS = "mn_term.t";

    /**
     * Appends the predicate {@code <fields> match <query>}.
     *
     * @param query The SQL buffer
     * @param fields The matched field expressions
     * @param queryExpression The query parameter expression
     * @param mode The match mode
     * @param options The boosts and fuzziness
     * @param target Where the entity data lives
     * @param appendExpression Renders a child expression
     * @param appendParameter Renders the query parameter with the given parameter converter class name
     */
    void appendTextMatch(StringBuilder query,
                         List<Expression<?>> fields,
                         Expression<String> queryExpression,
                         MatchMode mode,
                         TextMatchOptions options,
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
     * @param options The boosts and fuzziness
     * @param target Where the entity data lives
     * @param appendExpression Renders a child expression
     * @param appendParameter Renders the query parameter with the given parameter converter class name
     */
    void appendSearchScore(StringBuilder query,
                           List<Expression<?>> fields,
                           Expression<String> queryExpression,
                           MatchMode mode,
                           TextMatchOptions options,
                           Target target,
                           Consumer<Expression<?>> appendExpression,
                           Function<String, Consumer<Expression<?>>> appendParameter);

    /**
     * Renders the highlighted fragment of one field: the field's text with the matched terms wrapped in
     * {@link TextSearchSupport#HIGHLIGHT_START_TAG} and {@link TextSearchSupport#HIGHLIGHT_END_TAG}.
     *
     * @param query The query being built
     * @param field The field expression
     * @param queryExpression The search string parameter
     * @param mode The match mode
     * @param target Where the entity lives
     * @param appendExpression Renders an expression
     * @param appendParameter Creates a renderer binding the search string with the named converter
     * @throws UnsupportedSearchFeatureException if the dialect cannot highlight
     */
    void appendSearchHighlight(StringBuilder query,
                               Expression<?> field,
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
                return TextSearchSupport.resolveLanguage(property.getOwner());
            }
        }
        return DEFAULT_LANGUAGE;
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
    /**
     * @param field The field expression
     * @param options The options
     * @return The query-time boost of the field, or {@code null}
     */
    static @Nullable Double boostOf(Expression<?> field, TextMatchOptions options) {
        if (options.boosts().isEmpty() || !(field instanceof io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> propertyPath)) {
            return null;
        }
        return options.boostFor(propertyPath.getProperty().getName());
    }

    /**
     * @param boost The boost
     * @return The boost as a SQL literal, without a fractional part when it is whole
     */
    static String factor(double boost) {
        return boost == Math.rint(boost) ? String.valueOf((long) boost) : String.valueOf(boost);
    }

    /**
     * Rejects query-time boosts on a store without them.
     *
     * @param options The options
     * @param store The store name
     */
    static void rejectBoosts(TextMatchOptions options, String store) {
        if (!options.boosts().isEmpty()) {
            throw new UnsupportedSearchFeatureException(SearchCapability.BOOST, store);
        }
    }

    /**
     * Rejects an edit distance other than the default on a store whose fuzzy matching is fixed.
     *
     * @param options The options
     * @param store The store name
     */
    static void rejectFuzziness(TextMatchOptions options, String store) {
        if (options.fuzziness() > 1) {
            throw new UnsupportedSearchFeatureException(SearchCapability.FUZZINESS, store);
        }
    }

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
     * Renders the DDL of the index backing full-text search, for an entity with a {@code @Language} column.
     *
     * @param indexName The escaped index name
     * @param escapedTableName The escaped table name
     * @param columns The search field columns, escaped when needed
     * @param language The default text search language
     * @param languageColumn The escaped column holding the per-row language, or {@code null}
     * @return The {@code CREATE INDEX} statements
     */
    default List<String> buildSearchIndex(String indexName, String escapedTableName, List<SearchColumn> columns, String language, @Nullable String languageColumn) {
        return buildSearchIndex(indexName, escapedTableName, columns, language);
    }

    /**
     * Where the searched entity lives: a JSON column for JSON-represented entities, else plain columns.
     *
     * @param jsonColumn The JSON column holding the whole entity, or {@code null} for relational entities
     * @param tableAlias The table alias in the query, or {@code null}
     * @param languageColumn The rendered {@code @Language} column holding the per-row language, or {@code null}
     */
    record Target(@Nullable String jsonColumn, @Nullable String tableAlias, @Nullable String languageColumn) {
        /**
         * A relational target without alias.
         */
        public static final Target RELATIONAL = new Target(null, null, null);

        /**
         * @param jsonColumn The JSON column, or {@code null}
         * @param tableAlias The table alias, or {@code null}
         */
        public Target(@Nullable String jsonColumn, @Nullable String tableAlias) {
            this(jsonColumn, tableAlias, null);
        }
    }

    /**
     * A search field column for DDL rendering.
     *
     * @param column The column name, escaped when needed
     * @param weight The declared weight, or {@code null}
     * @param trigramIndex The escaped name of the {@code pg_trgm} index of a fuzzy field, or {@code null}
     */
    record SearchColumn(String column, @Nullable SearchWeight weight, @Nullable String trigramIndex) {

        /**
         * @param column The escaped column
         * @param weight The weight
         */
        SearchColumn(String column, @Nullable SearchWeight weight) {
            this(column, weight, null);
        }
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
                                    TextMatchOptions options,
                                    Target target,
                                    Consumer<Expression<?>> appendExpression,
                                    Function<String, Consumer<Expression<?>>> appendParameter) {
            rejectBoosts(options, "PostgreSQL text search");
            if (mode == MatchMode.FUZZY) {
                rejectFuzziness(options, "PostgreSQL pg_trgm");
                appendFuzzyMatch(query, fields, queryExpression, appendExpression, appendParameter);
                return;
            }
            String language = resolveLanguage(fields);
            appendVector(query, fields, language, target.languageColumn(), appendExpression);
            query.append(" @@ ");
            appendTsQuery(query, queryExpression, mode, language, appendParameter);
        }

        /**
         * Renders a fuzzy match with {@code pg_trgm}: a term matches a field when its word similarity
         * reaches {@code pg_trgm.word_similarity_threshold} ({@code <%}); any positive term may match and
         * no negated term may. The terms are bound as space-separated lists the query splits.
         */
        private void appendFuzzyMatch(StringBuilder query,
                                      List<Expression<?>> fields,
                                      Expression<String> queryExpression,
                                      Consumer<Expression<?>> appendExpression,
                                      Function<String, Consumer<Expression<?>>> appendParameter) {
            query.append("(EXISTS ");
            appendTermsQuery(query, fields, queryExpression, false, appendExpression, appendParameter);
            query.append(" AND NOT EXISTS ");
            appendTermsQuery(query, fields, queryExpression, true, appendExpression, appendParameter);
            query.append(')');
        }

        private void appendTermsQuery(StringBuilder query,
                                      List<Expression<?>> fields,
                                      Expression<String> queryExpression,
                                      boolean negated,
                                      Consumer<Expression<?>> appendExpression,
                                      Function<String, Consumer<Expression<?>>> appendParameter) {
            query.append("(SELECT 1 FROM ");
            appendTerms(query, queryExpression, negated, appendParameter);
            query.append(" WHERE ");
            for (int i = 0; i < fields.size(); i++) {
                if (i > 0) {
                    query.append(" OR ");
                }
                query.append(TERM_ALIAS).append(" <% ");
                appendExpression.accept(fields.get(i));
            }
            query.append(')');
        }

        private void appendTerms(StringBuilder query,
                                 Expression<String> queryExpression,
                                 boolean negated,
                                 Function<String, Consumer<Expression<?>>> appendParameter) {
            query.append("unnest(string_to_array(");
            appendParameter.apply(PostgresTsQueryConverter.fuzzyConverterFor(negated)).accept(queryExpression);
            query.append(",' ')) AS mn_term(t)");
        }

        /**
         * Renders the fuzzy relevance: the best word similarity of any term over the fields, each
         * multiplied by its weight.
         */
        private void appendFuzzyScore(StringBuilder query,
                                      List<Expression<?>> fields,
                                      Expression<String> queryExpression,
                                      Consumer<Expression<?>> appendExpression,
                                      Function<String, Consumer<Expression<?>>> appendParameter) {
            query.append("(SELECT coalesce(max(");
            if (fields.size() > 1) {
                query.append("greatest(");
            }
            for (int i = 0; i < fields.size(); i++) {
                if (i > 0) {
                    query.append(',');
                }
                query.append("word_similarity(").append(TERM_ALIAS).append(',');
                appendExpression.accept(fields.get(i));
                query.append(')');
                SearchWeight weight = resolveWeight(fields.get(i));
                if (weight != null) {
                    query.append(" * ").append(weight.numericWeight());
                }
            }
            if (fields.size() > 1) {
                query.append(')');
            }
            query.append("),0) FROM ");
            appendTerms(query, queryExpression, false, appendParameter);
            query.append(')');
        }

        @Override
        public void appendSearchScore(StringBuilder query,
                                      List<Expression<?>> fields,
                                      Expression<String> queryExpression,
                                      MatchMode mode,
                                      TextMatchOptions options,
                                      Target target,
                                      Consumer<Expression<?>> appendExpression,
                                      Function<String, Consumer<Expression<?>>> appendParameter) {
            rejectBoosts(options, "PostgreSQL text search");
            if (mode == MatchMode.FUZZY) {
                rejectFuzziness(options, "PostgreSQL pg_trgm");
                appendFuzzyScore(query, fields, queryExpression, appendExpression, appendParameter);
                return;
            }
            String language = resolveLanguage(fields);
            query.append("ts_rank_cd(");
            appendVector(query, fields, language, target.languageColumn(), appendExpression);
            query.append(',');
            appendTsQuery(query, queryExpression, mode, language, appendParameter);
            query.append(')');
        }

        @Override
        public void appendSearchHighlight(StringBuilder query,
                                          Expression<?> field,
                                          Expression<String> queryExpression,
                                          MatchMode mode,
                                          Target target,
                                          Consumer<Expression<?>> appendExpression,
                                          Function<String, Consumer<Expression<?>>> appendParameter) {
            String language = resolveLanguage(List.of(field));
            query.append("ts_headline('").append(language).append("',");
            appendExpression.accept(field);
            query.append(',');
            if (mode == MatchMode.FUZZY) {
                // ts_headline has no fuzzy query, so the terms themselves are highlighted where they occur
                query.append("plainto_tsquery('").append(language).append("',");
                appendParameter.apply(PostgresTsQueryConverter.fuzzyConverterFor(false)).accept(queryExpression);
                query.append(')');
            } else {
                appendTsQuery(query, queryExpression, mode, language, appendParameter);
            }
            query.append(",'StartSel=").append(TextSearchSupport.HIGHLIGHT_START_TAG)
                .append(",StopSel=").append(TextSearchSupport.HIGHLIGHT_END_TAG).append("')");
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
            return buildSearchIndex(indexName, escapedTableName, columns, language, null);
        }

        @Override
        public List<String> buildSearchIndex(String indexName, String escapedTableName, List<SearchColumn> columns, String language, @Nullable String languageColumn) {
            StringBuilder ddl = new StringBuilder("CREATE INDEX ").append(indexName).append(" ON ").append(escapedTableName).append(" USING GIN (");
            List<SearchWeight> weights = columns.stream().map(SearchColumn::weight).toList();
            List<Consumer<StringBuilder>> renderers = columns.stream().map(c -> (Consumer<StringBuilder>) sb -> sb.append(c.column())).toList();
            appendVector(ddl, renderers, weights, language, languageColumn);
            List<String> statements = new ArrayList<>(3);
            statements.add(ddl.append(");").toString());
            // a pg_trgm GIN index per column marked @SearchField(fuzzy = true), after the trusted extension
            boolean trigram = false;
            for (SearchColumn column : columns) {
                if (column.trigramIndex() != null) {
                    if (!trigram) {
                        statements.add("CREATE EXTENSION IF NOT EXISTS pg_trgm;");
                        trigram = true;
                    }
                    statements.add("CREATE INDEX " + column.trigramIndex() + " ON " + escapedTableName + " USING GIN (" + column.column() + " gin_trgm_ops);");
                }
            }
            return statements;
        }

        private void appendVector(StringBuilder query,
                                  List<Expression<?>> fields,
                                  String language,
                                  @Nullable String languageColumn,
                                  Consumer<Expression<?>> appendExpression) {
            List<SearchWeight> weights = fields.stream().map(TextSearchDialect::resolveWeight).toList();
            List<Consumer<StringBuilder>> renderers = fields.stream().map(f -> (Consumer<StringBuilder>) sb -> appendExpression.accept(f)).toList();
            appendVector(query, renderers, weights, language, languageColumn);
        }

        /**
         * Renders the combined {@code tsvector} of the fields; the same shape is used by the query and
         * by the expression index so PostgreSQL can match them.
         */
        private static void appendVector(StringBuilder query,
                                         List<Consumer<StringBuilder>> fieldRenderers,
                                         List<SearchWeight> weights,
                                         String language,
                                         @Nullable String languageColumn) {
            // a @Language column selects the text search configuration per row, the entity language is its fallback
            String config = languageColumn == null
                ? "'" + language + "'"
                : "coalesce(" + languageColumn + ",'" + language + "')::regconfig";
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
                query.append("to_tsvector(").append(config).append(',');
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

        /**
         * The Oracle Text policy highlights are rendered with; schema generation creates it with the
         * default lexer, matching the {@code CONTEXT} indexes.
         */
        public static final String POLICY_NAME = "MN_SEARCH_POLICY";

        private static final String CONTAINS_SUFFIX = ") > 0";

        @Override
        public void appendTextMatch(StringBuilder query,
                                    List<Expression<?>> fields,
                                    Expression<String> queryExpression,
                                    MatchMode mode,
                                    TextMatchOptions options,
                                    Target target,
                                    Consumer<Expression<?>> appendExpression,
                                    Function<String, Consumer<Expression<?>>> appendParameter) {
            rejectFuzziness(options, "Oracle Text");
            if (fields.size() == 1) {
                appendContains(query, fields.get(0), 1, queryExpression, target, appendExpression,
                    appendParameter.apply(OracleTextQueryConverter.converterFor(mode, OracleTextQueryConverter.Part.FULL)));
                return;
            }
            query.append("((");
            Consumer<Expression<?>> positive = appendParameter.apply(OracleTextQueryConverter.converterFor(mode, OracleTextQueryConverter.Part.POSITIVE));
            appendAnyField(query, fields, 1, queryExpression, target, appendExpression, positive);
            query.append(") AND NOT (");
            Consumer<Expression<?>> negated = appendParameter.apply(OracleTextQueryConverter.converterFor(mode, OracleTextQueryConverter.Part.NEGATED));
            appendAnyField(query, fields, fields.size() + 1, queryExpression, target, appendExpression, negated);
            query.append("))");
        }

        @Override
        public void appendSearchScore(StringBuilder query,
                                      List<Expression<?>> fields,
                                      Expression<String> queryExpression,
                                      MatchMode mode,
                                      TextMatchOptions options,
                                      Target target,
                                      Consumer<Expression<?>> appendExpression,
                                      Function<String, Consumer<Expression<?>>> appendParameter) {
            rejectFuzziness(options, "Oracle Text");
            if (fields.size() == 1) {
                query.append("SCORE(1)");
                Double boost = boostOf(fields.get(0), options);
                if (boost != null) {
                    query.append(" * ").append(factor(boost));
                }
                return;
            }
            boolean weighted = !options.boosts().isEmpty() || fields.stream().anyMatch(f -> resolveWeight(f) != null);
            query.append('(');
            for (int i = 0; i < fields.size(); i++) {
                if (i > 0) {
                    query.append(" + ");
                }
                query.append("SCORE(").append(i + 1).append(')');
                if (weighted) {
                    query.append(" * ").append(scoreFactor(fields.get(i), options));
                }
            }
            query.append(')');
        }

        /** A query-time boost replaces the index-time weight of the field. */
        private String scoreFactor(Expression<?> field, TextMatchOptions options) {
            Double boost = boostOf(field, options);
            if (boost != null) {
                return factor(boost);
            }
            SearchWeight weight = resolveWeight(field);
            return String.valueOf(weight == null ? 1 : weight.numericWeight());
        }

        /**
         * Renders {@code CTX_DOC.POLICY_SNIPPET(policy, document, query, starttag, endtag)} over the field's
         * text, which needs the {@link #POLICY_NAME} policy created by schema generation but no index key.
         * A JSON entity highlights {@code JSON_VALUE(column, '$.path')}.
         */
        @Override
        public void appendSearchHighlight(StringBuilder query,
                                          Expression<?> field,
                                          Expression<String> queryExpression,
                                          MatchMode mode,
                                          Target target,
                                          Consumer<Expression<?>> appendExpression,
                                          Function<String, Consumer<Expression<?>>> appendParameter) {
            query.append("CTX_DOC.POLICY_SNIPPET('").append(POLICY_NAME).append("',");
            if (target.jsonColumn() != null && field instanceof io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> propertyPath) {
                query.append("JSON_VALUE(");
                if (target.tableAlias() != null) {
                    query.append(target.tableAlias()).append('.');
                }
                query.append(target.jsonColumn()).append(",'$.").append(propertyPath.getPropertyPath().getPath()).append("')");
            } else {
                appendExpression.accept(field);
            }
            query.append(',');
            appendParameter.apply(OracleTextQueryConverter.converterFor(mode, OracleTextQueryConverter.Part.FULL)).accept(queryExpression);
            query.append(",starttag => '").append(TextSearchSupport.HIGHLIGHT_START_TAG)
                .append("',endtag => '").append(TextSearchSupport.HIGHLIGHT_END_TAG).append("')");
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
            statements.add("BEGIN CTX_DDL.CREATE_POLICY('" + POLICY_NAME + "'); EXCEPTION WHEN OTHERS THEN IF SQLCODE != -20000 THEN RAISE; END IF; END;");
            return statements;
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
                query.append(target.jsonColumn()).append(",'$.").append(propertyPath.getPropertyPath().getPath()).append("',");
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
