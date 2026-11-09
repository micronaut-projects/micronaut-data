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
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Renders full-text search predicates and scores for a SQL dialect.
 *
 * @since 5.3.0
 */
@Internal
public interface TextSearchDialect {

    /** The start of an index DDL statement. */
    String CREATE_INDEX = "CREATE INDEX ";

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
     * @param options The match options, with the language override when set
     * @param target Where the entity lives
     * @param appendExpression Renders an expression
     * @param appendParameter Creates a renderer binding the search string with the named converter
     * @throws UnsupportedSearchFeatureException if the dialect cannot highlight
     */
    void appendSearchHighlight(StringBuilder query,
                               Expression<?> field,
                               Expression<String> queryExpression,
                               MatchMode mode,
                               TextMatchOptions options,
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
     * Resolves the language of the entity owning the given fields, checked to be a configuration name as it
     * is rendered inside the statement.
     *
     * @param fields The field expressions
     * @return The language, {@link #DEFAULT_LANGUAGE} when unset
     */
    static String resolveLanguage(List<Expression<?>> fields) {
        for (Expression<?> field : fields) {
            PersistentProperty property = asProperty(field);
            if (property != null) {
                return TextSearchSupport.requireLanguageName(TextSearchSupport.resolveLanguage(property.getOwner()));
            }
        }
        return DEFAULT_LANGUAGE;
    }

    /**
     * Checks that a search index name is a plain SQL identifier, as it is written into the {@code CREATE INDEX}
     * statement: a letter or underscore followed by letters, digits and underscores. A quote or a semicolon would
     * otherwise change the generated statement.
     *
     * @param indexName The index name
     * @return The index name
     * @throws IllegalArgumentException When the name holds any other character
     */
    static String requireIndexName(String indexName) {
        if (!indexName.matches("[A-Za-z_]\\w*")) {
            throw new IllegalArgumentException("A search index name must be an identifier of letters, digits and underscores: " + indexName);
        }
        return indexName;
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
     * @param field The field expression
     * @param options The options
     * @return The query-time boost of the field, by its property path, or {@code null}
     */
    static @Nullable Double boostOf(Expression<?> field, TextMatchOptions options) {
        if (options.boosts().isEmpty() || !(field instanceof io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> propertyPath)) {
            return null;
        }
        return options.boostFor(propertyPath.getPathAsString());
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

    /**
     * Rejects a language override on a store whose analysis is fixed per index.
     *
     * @param options The options
     * @param store The store name
     */
    static void rejectLanguage(TextMatchOptions options, String store) {
        if (options.language() != null) {
            throw new UnsupportedSearchFeatureException(SearchCapability.LANGUAGE_OVERRIDE, store);
        }
    }

    /**
     * @param fields The searched fields
     * @param options The options
     * @return The options' language override, else the language of the entity owning the fields
     */
    static String resolveLanguage(List<Expression<?>> fields, TextMatchOptions options) {
        // the language is rendered inside the statement, so a query-time value is checked before it gets there
        return options.language() != null ? TextSearchSupport.requireLanguageName(options.language()) : resolveLanguage(fields);
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
     * Renders the DDL of the index backing full-text search, with store-specific index parameters.
     *
     * @param indexName The escaped index name
     * @param escapedTableName The escaped table name
     * @param columns The search field columns, escaped when needed
     * @param language The default text search language
     * @param languageColumn The escaped column holding the per-row language, or {@code null}
     * @param parameters The store's index parameters ({@code @OracleTextIndex}), or {@code null} for its defaults
     * @return The {@code CREATE INDEX} statements
     * @since 5.4.0
     */
    default List<String> buildSearchIndex(String indexName, String escapedTableName, List<SearchColumn> columns, String language, @Nullable String languageColumn, @Nullable String parameters) {
        return buildSearchIndex(indexName, escapedTableName, columns, language, languageColumn);
    }

    /**
     * Renders the DDL of the JSON search index with store-specific index parameters.
     *
     * @param indexName The escaped index name
     * @param escapedTableName The escaped table name
     * @param escapedJsonColumn The escaped JSON column holding the whole entity
     * @param parameters The store's index parameters, or {@code null} for its defaults
     * @return The {@code CREATE INDEX} statements, empty when the dialect needs no index
     * @since 5.4.0
     */
    default List<String> buildJsonSearchIndex(String indexName, String escapedTableName, String escapedJsonColumn, @Nullable String parameters) {
        return buildJsonSearchIndex(indexName, escapedTableName, escapedJsonColumn);
    }

    /**
     * The names of the indexes {@link #buildSearchIndex} creates, escaped like its arguments.
     *
     * @param indexName The escaped index name
     * @param columns The search field columns
     * @return The index names, empty when the dialect needs no index
     * @since 5.3.0
     */
    default List<String> searchIndexNames(String indexName, List<SearchColumn> columns) {
        return List.of();
    }

    /**
     * @param indexName An escaped index name
     * @param schema The escaped schema of the entity's table, or {@code null} when it declares none
     * @return The statement dropping it
     * @since 5.3.0
     */
    default String buildDropSearchIndex(String indexName, @Nullable String schema) {
        return "DROP INDEX " + indexName;
    }

    /**
     * Renders the statements making the pending writes of the indexes searchable.
     *
     * @param indexNames The escaped index names
     * @return The statements, empty when the indexes are always current
     * @since 5.3.0
     */
    default List<String> buildSyncSearchIndex(List<String> indexNames) {
        return List.of();
    }

    /**
     * @param schema The escaped schema of the entity's table, or {@code null} when it declares none
     * @return A query counting the indexes with the name bound as its one parameter, in the form
     * {@link #catalogIndexName} gives, among the indexes of the entity, or {@code null} when the dialect cannot tell
     * @since 5.3.0
     */
    @Nullable
    default String searchIndexExistsQuery(@Nullable String schema) {
        return null;
    }

    /**
     * @param indexName An escaped index name
     * @return The name as the catalog stores it
     * @since 5.3.0
     */
    default String catalogIndexName(String indexName) {
        return indexName.replace("\"", "");
    }

    /**
     * Renders the DDL of the index backing full-text search over an entity stored as one JSON column,
     * if the dialect has one.
     *
     * @param indexName The escaped index name
     * @param escapedTableName The escaped table name
     * @param escapedJsonColumn The escaped JSON column holding the whole entity
     * @return The {@code CREATE INDEX} statements, empty when the dialect needs no index
     * @since 5.3.0
     */
    default List<String> buildJsonSearchIndex(String indexName, String escapedTableName, String escapedJsonColumn) {
        return List.of();
    }

    /**
     * The path of a property's keys in the JSON document of a JSON-represented entity. Each step is renamed
     * when its property declares its key with {@code @SerdeConfig(property)} or {@code @JsonProperty}; a naming
     * strategy of the serializer, such as {@code @JsonNaming}, is not applied, nor is {@code @MappedProperty},
     * which names columns rather than JSON keys.
     *
     * @param propertyPath A property path of a JSON-represented entity
     * @return The path of its keys in the JSON document
     */
    static String jsonKeyPath(io.micronaut.data.model.PersistentPropertyPath propertyPath) {
        String path = propertyPath.getPath();
        List<PersistentProperty> steps = propertyPath.getPropertyPath();
        java.util.StringJoiner names = new java.util.StringJoiner(".");
        java.util.StringJoiner keys = new java.util.StringJoiner(".");
        for (PersistentProperty step : steps) {
            names.add(step.getName());
            keys.add(jsonKey(step));
        }
        if (path.equals(names.toString())) {
            return keys.toString();
        }
        // a path whose steps are not all listed as associations, such as a joined path: only the leaf is renamed
        PersistentProperty property = propertyPath.getProperty();
        String key = jsonKey(property);
        if (key.equals(property.getName()) || !path.endsWith(property.getName())) {
            return path;
        }
        return path.substring(0, path.length() - property.getName().length()) + key;
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
     * @param languageColumn The rendered {@code @Language} column holding the per-row language, or {@code null}
     * @param labels The {@code CONTAINS} labels of the statement being built, or {@code null} to start at 1
     */
    record Target(@Nullable String jsonColumn, @Nullable String tableAlias, @Nullable String languageColumn, @Nullable Labels labels) {
        /**
         * A relational target without alias.
         */
        public static final Target RELATIONAL = new Target(null, null, null, null);

        /**
         * @param jsonColumn The JSON column holding the whole entity, or {@code null} for relational entities
         * @param tableAlias The table alias in the query, or {@code null}
         */
        public Target(@Nullable String jsonColumn, @Nullable String tableAlias) {
            this(jsonColumn, tableAlias, null, null);
        }

        /**
         * @param jsonColumn The JSON column holding the whole entity, or {@code null} for relational entities
         * @param tableAlias The table alias in the query, or {@code null}
         * @param languageColumn The rendered {@code @Language} column, or {@code null}
         */
        public Target(@Nullable String jsonColumn, @Nullable String tableAlias, @Nullable String languageColumn) {
            this(jsonColumn, tableAlias, languageColumn, null);
        }

        /**
         * @param labels The labels of the statement being built
         * @return This target with the statement's labels
         */
        public Target withLabels(Labels labels) {
            return new Target(jsonColumn, tableAlias, languageColumn, labels);
        }
    }

    /**
     * The {@code CONTAINS} labels of one statement. Each text match takes its own range, and its score,
     * rendered in the select list and the order, reads the same labels: a match is known by its fields
     * and its query parameter, as each clause holds its own copy of the expressions.
     *
     * <p>Two matches with the same key, such as two criteria text matches on one field whose parameters
     * are both unnamed, still take a range each, as Oracle rejects a label used by two {@code CONTAINS}
     * calls (ORA-29907); a score with that key reads the range of the first.</p>
     */
    final class Labels {
        private final java.util.Map<String, Integer> bases = new java.util.HashMap<>();
        private final java.util.Set<String> matched = new java.util.HashSet<>();
        private int next = 1;

        /**
         * The first label a score reads: the range of the first match with the same key.
         *
         * @param fields The fields of the text match
         * @param queryExpression The query expression of the text match
         * @param count The number of labels the match uses
         * @return The first label of the match
         */
        public int base(List<Expression<?>> fields, Expression<?> queryExpression, int count) {
            return bases.computeIfAbsent(key(fields, queryExpression), k -> allocate(count));
        }

        /**
         * The first label of a {@code CONTAINS} call: the range a score with the same key reads when this is
         * the first match with that key, else a new range.
         *
         * @param fields The fields of the text match
         * @param queryExpression The query expression of the text match
         * @param count The number of labels the match uses
         * @return The first label of the match
         */
        public int match(List<Expression<?>> fields, Expression<?> queryExpression, int count) {
            String key = key(fields, queryExpression);
            if (matched.add(key)) {
                return base(fields, queryExpression, count);
            }
            return allocate(count);
        }

        private int allocate(int count) {
            int base = next;
            next += count;
            return base;
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

        /**
         * The immutable wrapper of the {@code text} to {@code regconfig} cast that a per-row {@code @Language}
         * column needs inside an index expression; schema generation creates it.
         */
        public static final String REGCONFIG_FUNCTION = "mn_regconfig";
        private static final String REGCONFIG_FUNCTION_DDL = "CREATE OR REPLACE FUNCTION " + REGCONFIG_FUNCTION
            + "(cfg text) RETURNS regconfig AS 'SELECT cfg::regconfig' LANGUAGE sql IMMUTABLE SET search_path = pg_catalog, pg_temp;";
        // PostgreSQL can skip an index that exists, so schema generation can run against an existing database
        private static final String CREATE_INDEX_IF_NOT_EXISTS = "CREATE INDEX IF NOT EXISTS ";
        private static final String POSTGRESQL_TEXT_SEARCH = "PostgreSQL text search";
        private static final String PG_TRGM = "PostgreSQL pg_trgm";

        @Override
        public void appendTextMatch(StringBuilder query,
                                    List<Expression<?>> fields,
                                    Expression<String> queryExpression,
                                    MatchMode mode,
                                    TextMatchOptions options,
                                    Target target,
                                    Consumer<Expression<?>> appendExpression,
                                    Function<String, Consumer<Expression<?>>> appendParameter) {
            rejectBoosts(options, POSTGRESQL_TEXT_SEARCH);
            if (mode == MatchMode.FUZZY) {
                rejectTrigramOptions(options);
                appendFuzzyMatch(query, fields, queryExpression, appendExpression, appendParameter);
                return;
            }
            String language = resolveLanguage(fields, options);
            appendVector(query, fields, language, languageColumn(target, options), appendExpression);
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
            rejectBoosts(options, POSTGRESQL_TEXT_SEARCH);
            if (mode == MatchMode.FUZZY) {
                rejectTrigramOptions(options);
                appendFuzzyScore(query, fields, queryExpression, appendExpression, appendParameter);
                return;
            }
            String language = resolveLanguage(fields, options);
            query.append("ts_rank_cd(");
            appendVector(query, fields, language, languageColumn(target, options), appendExpression);
            query.append(',');
            appendTsQuery(query, queryExpression, mode, language, appendParameter);
            query.append(')');
        }

        @Override
        public void appendSearchHighlight(StringBuilder query,
                                          Expression<?> field,
                                          Expression<String> queryExpression,
                                          MatchMode mode,
                                          TextMatchOptions options,
                                          Target target,
                                          Consumer<Expression<?>> appendExpression,
                                          Function<String, Consumer<Expression<?>>> appendParameter) {
            String language = resolveLanguage(List.of(field), options);
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

        /**
         * Rejects the options a {@code pg_trgm} match cannot honour: it has a fixed similarity threshold rather
         * than an edit distance, and compares character trigrams with no text search configuration.
         */
        private static void rejectTrigramOptions(TextMatchOptions options) {
            rejectFuzziness(options, PG_TRGM);
            rejectLanguage(options, PG_TRGM);
        }

        /**
         * A language override analyses both the query and the rows with it, so a row whose {@code @Language}
         * column holds another configuration is matched with the same stemming as the query terms.
         */
        private static @Nullable String languageColumn(Target target, TextMatchOptions options) {
            return options.language() != null ? null : target.languageColumn();
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
        public List<String> searchIndexNames(String indexName, List<SearchColumn> columns) {
            List<String> names = new ArrayList<>(1 + columns.size());
            names.add(indexName);
            for (SearchColumn column : columns) {
                if (column.trigramIndex() != null) {
                    names.add(column.trigramIndex());
                }
            }
            return names;
        }

        @Override
        public String buildDropSearchIndex(String indexName, @Nullable String schema) {
            // an index lives in the schema of its table, which the search path may not reach
            return "DROP INDEX IF EXISTS " + (schema == null ? indexName : schema + "." + indexName) + ";";
        }

        @Override
        public String searchIndexExistsQuery(@Nullable String schema) {
            // index names are unique per schema, and an index is created in the schema of its table
            String schemaName = schema == null ? "current_schema()" : "'" + catalogIndexName(schema).replace("'", "''") + "'";
            return "SELECT count(*) FROM pg_indexes WHERE schemaname = " + schemaName + " AND indexname = ?";
        }

        @Override
        public String catalogIndexName(String indexName) {
            // an unquoted identifier is stored in lower case
            return indexName.startsWith("\"") ? indexName.replace("\"", "") : indexName.toLowerCase(Locale.ROOT);
        }

        @Override
        public List<String> buildSearchIndex(String indexName, String escapedTableName, List<SearchColumn> columns, String language, @Nullable String languageColumn) {
            StringBuilder ddl = new StringBuilder(CREATE_INDEX_IF_NOT_EXISTS).append(indexName).append(" ON ").append(escapedTableName).append(" USING GIN (");
            List<SearchWeight> weights = columns.stream().map(SearchColumn::weight).toList();
            List<Consumer<StringBuilder>> renderers = columns.stream().map(c -> (Consumer<StringBuilder>) sb -> sb.append(c.column())).toList();
            appendVector(ddl, renderers, weights, language, languageColumn);
            List<String> statements = new ArrayList<>(4);
            if (languageColumn != null) {
                // the text to regconfig cast is only stable, so an index over a per-row configuration needs an immutable wrapper
                statements.add(REGCONFIG_FUNCTION_DDL);
            }
            statements.add(ddl.append(");").toString());
            // a pg_trgm GIN index per column marked @SearchField(fuzzy = true), after the trusted extension
            boolean trigram = false;
            for (SearchColumn column : columns) {
                if (column.trigramIndex() != null) {
                    if (!trigram) {
                        statements.add("CREATE EXTENSION IF NOT EXISTS pg_trgm;");
                        trigram = true;
                    }
                    statements.add(CREATE_INDEX_IF_NOT_EXISTS + column.trigramIndex() + " ON " + escapedTableName + " USING GIN (" + column.column() + " gin_trgm_ops);");
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
            // a @Language column selects the text search configuration per row, the entity language is its fallback;
            // the cast goes through an immutable function so the expression can be indexed
            String config = languageColumn == null
                ? "'" + language + "'"
                : REGCONFIG_FUNCTION + "(coalesce(" + languageColumn + ",'" + language + "'))";
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
     * where any field matches a negated term; a phrase search has no negated terms and renders no exclusion.
     * The {@link OracleTextQueryConverter} decides at bind time which terms each part holds. JSON-represented entities
     * get one JSON search index over the column and use {@code CONTAINS(column, '(' || ? || ') INPATH (/path)', n) > 0},
     * which unlike {@code JSON_TEXTCONTAINS} carries a label for {@code SCORE(n)}.</p>
     */
    enum OracleTextSearchDialect implements TextSearchDialect {
        INSTANCE;

        /**
         * The Oracle Text policy highlights are rendered with; schema generation creates it with the
         * default lexer, matching the {@code CONTEXT} indexes.
         */
        public static final String POLICY_NAME = "MN_SEARCH_POLICY";
        private static final String ORACLE_TEXT = "Oracle Text";
        private static final String DEFAULT_INDEX_PARAMETERS = "SYNC (ON COMMIT)";

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
            rejectFuzziness(options, ORACLE_TEXT);
            rejectLanguage(options, ORACLE_TEXT);
            Labels labels = target.labels();
            int base = labels == null ? 1 : labels.match(fields, queryExpression, labelCount(fields, mode));
            if (fields.size() == 1) {
                appendContains(query, fields.get(0), base, queryExpression, target, appendExpression,
                    appendParameter.apply(OracleTextQueryConverter.converterFor(mode, OracleTextQueryConverter.Part.FULL)));
                return;
            }
            if (mode == MatchMode.NATIVE) {
                // the caller's CONTAINS query runs against every field as it is; its own NOT excludes
                query.append('(');
                appendAnyField(query, fields, base, queryExpression, target, appendExpression,
                    appendParameter.apply(OracleTextQueryConverter.converterFor(mode, OracleTextQueryConverter.Part.FULL)));
                query.append(')');
                return;
            }
            Consumer<Expression<?>> positive = appendParameter.apply(OracleTextQueryConverter.converterFor(mode, OracleTextQueryConverter.Part.POSITIVE));
            if (mode == MatchMode.PHRASE) {
                // a phrase has no negated terms: a - inside it is part of the phrase
                query.append('(');
                appendAnyField(query, fields, base, queryExpression, target, appendExpression, positive);
                query.append(')');
                return;
            }
            query.append("((");
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
                                      TextMatchOptions options,
                                      Target target,
                                      Consumer<Expression<?>> appendExpression,
                                      Function<String, Consumer<Expression<?>>> appendParameter) {
            rejectFuzziness(options, ORACLE_TEXT);
            rejectLanguage(options, ORACLE_TEXT);
            Labels labels = target.labels();
            int base = labels == null ? 1 : labels.base(fields, queryExpression, labelCount(fields, mode));
            if (fields.size() == 1) {
                query.append("SCORE(").append(base).append(')');
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
                query.append("SCORE(").append(base + i).append(')');
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
         * A JSON entity highlights {@code JSON_VALUE(column, '$.path' RETURNING CLOB)}.
         */
        @Override
        public void appendSearchHighlight(StringBuilder query,
                                          Expression<?> field,
                                          Expression<String> queryExpression,
                                          MatchMode mode,
                                          TextMatchOptions options,
                                          Target target,
                                          Consumer<Expression<?>> appendExpression,
                                          Function<String, Consumer<Expression<?>>> appendParameter) {
            rejectLanguage(options, ORACLE_TEXT);
            query.append("CTX_DOC.POLICY_SNIPPET('").append(POLICY_NAME).append("',");
            if (target.jsonColumn() != null && field instanceof io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> propertyPath) {
                query.append("JSON_VALUE(");
                if (target.tableAlias() != null) {
                    query.append(target.tableAlias()).append('.');
                }
                // RETURNING CLOB, as the default VARCHAR2(4000) is NULL for a longer value
                query.append(target.jsonColumn()).append(",'$.").append(jsonKeyPath(propertyPath.getPropertyPath())).append("' RETURNING CLOB)");
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
            return buildSearchIndex(indexName, escapedTableName, columns, language, null, null);
        }

        @Override
        public List<String> buildSearchIndex(String indexName, String escapedTableName, List<SearchColumn> columns, String language, @Nullable String languageColumn, @Nullable String parameters) {
            List<String> statements = new java.util.ArrayList<>(columns.size());
            for (SearchColumn column : columns) {
                statements.add(CREATE_INDEX + columnIndexName(indexName, column) + " ON " + escapedTableName + " (" + column.column()
                    + ") INDEXTYPE IS CTXSYS.CONTEXT PARAMETERS ('" + indexParameters(parameters) + "');");
            }
            statements.add(createPolicyStatement());
            return statements;
        }

        private static String indexParameters(@Nullable String parameters) {
            return parameters == null || parameters.isBlank() ? DEFAULT_INDEX_PARAMETERS : parameters.replace("'", "''");
        }

        @Override
        public List<String> searchIndexNames(String indexName, List<SearchColumn> columns) {
            List<String> names = new java.util.ArrayList<>(columns.size());
            for (SearchColumn column : columns) {
                names.add(columnIndexName(indexName, column));
            }
            return names;
        }

        @Override
        public List<String> buildSyncSearchIndex(List<String> indexNames) {
            List<String> statements = new java.util.ArrayList<>(indexNames.size());
            for (String name : indexNames) {
                statements.add("BEGIN CTX_DDL.SYNC_INDEX('" + catalogIndexName(name) + "'); END;");
            }
            return statements;
        }

        @Override
        public String searchIndexExistsQuery(@Nullable String schema) {
            // the index name is not qualified, so the index is created in the current schema whatever the table's
            return "SELECT count(*) FROM user_indexes WHERE index_name = ?";
        }

        @Override
        public String catalogIndexName(String indexName) {
            // an unquoted identifier is stored in upper case
            return indexName.startsWith("\"") ? indexName.replace("\"", "") : indexName.toUpperCase(java.util.Locale.ROOT);
        }

        private static String columnIndexName(String indexName, SearchColumn column) {
            return indexName.endsWith("\"")
                ? indexName.substring(0, indexName.length() - 1) + "_" + column.column().replace("\"", "") + "\""
                : indexName + "_" + column.column();
        }

        @Override
        public List<String> buildJsonSearchIndex(String indexName, String escapedTableName, String escapedJsonColumn) {
            return buildJsonSearchIndex(indexName, escapedTableName, escapedJsonColumn, null);
        }

        @Override
        public List<String> buildJsonSearchIndex(String indexName, String escapedTableName, String escapedJsonColumn, @Nullable String parameters) {
            return List.of(
                "CREATE SEARCH INDEX " + indexName + " ON " + escapedTableName + " (" + escapedJsonColumn + ") FOR JSON PARAMETERS ('" + indexParameters(parameters) + "');",
                createPolicyStatement()
            );
        }

        private static String createPolicyStatement() {
            // ORA-20000 carries every Oracle Text error; only DRG-10507 says the policy exists already
            return "BEGIN CTX_DDL.CREATE_POLICY('" + POLICY_NAME + "'); EXCEPTION WHEN OTHERS THEN IF SQLCODE != -20000 OR INSTR(SQLERRM, 'DRG-10507') = 0 THEN RAISE; END IF; END;";
        }

        /**
         * The number of labels a match takes from the statement's labels, so two matches in one statement
         * never share a label: one for a single field; for several fields one per field for the positive
         * terms and, unless the match is a phrase, one per field for the negated ones.
         */
        private static int labelCount(List<Expression<?>> fields, MatchMode mode) {
            if (fields.size() == 1) {
                return 1;
            }
            return mode == MatchMode.PHRASE ? fields.size() : 2 * fields.size();
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
                // CONTAINS with INPATH on the JSON search index: unlike JSON_TEXTCONTAINS it carries a label, so SCORE(n) works
                query.append("CONTAINS(");
                if (target.tableAlias() != null) {
                    query.append(target.tableAlias()).append('.');
                }
                query.append(target.jsonColumn()).append(",'(' || ");
                appendQuery.accept(queryExpression);
                query.append(" || ') INPATH (/").append(TextSearchDialect.jsonKeyPath(propertyPath.getPropertyPath()).replace('.', '/')).append(")',")
                    .append(label).append(CONTAINS_SUFFIX);
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
