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
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.query.builder.QueryParameterBinding;
import io.micronaut.data.model.runtime.convert.PostgresTsQueryConverter;
import io.micronaut.data.model.search.MatchMode;
import io.micronaut.data.model.search.SearchCapability;
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
    String DEFAULT_LANGUAGE = "english";

    /**
     * Appends the predicate {@code <fields> match <query>}.
     *
     * @param query The SQL buffer
     * @param fields The matched field expressions
     * @param queryExpression The query parameter expression
     * @param mode The match mode
     * @param appendExpression Renders a child expression
     * @param appendParameter Renders the query parameter with the given parameter converter class name
     */
    void appendTextMatch(StringBuilder query,
                         List<Expression<?>> fields,
                         Expression<String> queryExpression,
                         MatchMode mode,
                         Consumer<Expression<?>> appendExpression,
                         Function<String, Consumer<Expression<?>>> appendParameter);

    /**
     * Appends the relevance score expression for the match.
     *
     * @param query The SQL buffer
     * @param fields The matched field expressions
     * @param queryExpression The query parameter expression
     * @param mode The match mode
     * @param appendExpression Renders a child expression
     * @param appendParameter Renders the query parameter with the given parameter converter class name
     */
    void appendSearchScore(StringBuilder query,
                           List<Expression<?>> fields,
                           Expression<String> queryExpression,
                           MatchMode mode,
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
                PersistentEntity owner = property.getOwner();
                String language = owner.getAnnotationMetadata().stringValue(Searchable.class, Searchable.MEMBER_LANGUAGE).orElse(null);
                if (language != null && !language.isEmpty()) {
                    return requireLanguageName(language);
                }
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
        return new DelegatingBuilderParameterBinding(binding) {
            @Override
            public String getConverterClassName() {
                return converterClassName;
            }
        };
    }

    /**
     * Renders the DDL of the index backing full-text search on the given columns, if the dialect
     * has one.
     *
     * @param indexName The escaped index name
     * @param escapedTableName The escaped table name
     * @param columns The search field columns, escaped when needed
     * @param language The text search language
     * @return The {@code CREATE INDEX} statement, or {@code null} when the dialect needs no index
     */
    @Nullable
    default String buildSearchIndex(String indexName, String escapedTableName, List<SearchColumn> columns, String language) {
        return null;
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
        public String buildSearchIndex(String indexName, String escapedTableName, List<SearchColumn> columns, String language) {
            StringBuilder ddl = new StringBuilder("CREATE INDEX IF NOT EXISTS ").append(indexName).append(" ON ").append(escapedTableName).append(" USING GIN (");
            List<SearchWeight> weights = columns.stream().map(SearchColumn::weight).toList();
            List<Consumer<StringBuilder>> renderers = columns.stream().map(c -> (Consumer<StringBuilder>) sb -> sb.append(c.column())).toList();
            appendVector(ddl, renderers, weights, language);
            return ddl.append(");").toString();
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
}
