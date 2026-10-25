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
package io.micronaut.data.processor.graph.matchers;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.ParameterExpression;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.graph.annotation.GraphRepository;
import io.micronaut.data.graph.model.query.builder.GraphQueryBuilder;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.query.BindingParameter.BindingContext;
import io.micronaut.data.model.query.builder.QueryParameterBinding;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.visitors.MatchFailedException;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.data.processor.visitors.finders.FindersUtils;
import io.micronaut.data.processor.visitors.finders.MethodMatchInfo;
import io.micronaut.data.processor.visitors.finders.MethodMatcher;
import io.micronaut.data.processor.visitors.finders.RawQueryMethodMatcher;
import io.micronaut.data.processor.visitors.finders.TypeUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ParameterElement;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Matches repository methods of graph repositories annotated with {@link Query}.
 *
 * <p>Parameters are written as {@code $name}, as in Cypher and GQL, and bound by name. The operation type is
 * derived from the clauses of the query: a query with a {@code RETURN} clause whose method returns
 * results is a query, otherwise {@code DELETE} makes it a delete and {@code CREATE}, {@code MERGE},
 * {@code SET} or {@code REMOVE} an update.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public final class GraphRawQueryMethodMatcher implements MethodMatcher {

    /**
     * A parameter reference. A dollar inside a name, as in Oracle's {@code v$session}, is not one.
     */
    private static final Pattern PARAMETER_PATTERN = Pattern.compile("(?<![\\w$])\\$([a-zA-Z_]\\w*)");
    private static final Pattern WRITE_PATTERN = Pattern.compile("\\b(CREATE|MERGE|SET|REMOVE|INSERT|UPDATE)\\b");
    private static final Pattern DELETE_PATTERN = Pattern.compile("\\bDELETE\\b");
    private static final Pattern RETURN_PATTERN = Pattern.compile("\\bRETURN\\b");
    private static final Pattern SELECT_PATTERN = Pattern.compile("^\\s*SELECT\\b");
    /**
     * Names that are not clauses: parameters, property accesses, labels and relationship types,
     * map keys and aliases. They are blanked before the clause keywords are looked for, so that a
     * property called {@code set} or a parameter called {@code merge} does not turn a read into a write.
     */
    private static final Pattern NAME_PATTERN = Pattern.compile("\\$\\w+|[.:]\\s*\\w+|\\bAS\\s+\\w+", Pattern.CASE_INSENSITIVE);
    private static final Pattern MAP_KEY_PATTERN = Pattern.compile("\\w+\\s*:");

    @Override
    public int getOrder() {
        // Before RawQueryMethodMatcher, which parses SQL
        return DEFAULT_POSITION - 2000;
    }

    @Override
    public @Nullable MethodMatch match(MethodMatchContext matchContext) {
        if (!matchContext.getRepositoryClass().hasStereotype(GraphRepository.class)
            || !(matchContext.getQueryBuilder() instanceof GraphQueryBuilder graphQueryBuilder)) {
            return null;
        }
        String query = matchContext.getMethodElement().stringValue(Query.class).orElse(null);
        if (query == null) {
            return null;
        }
        // Languages without $name parameters, such as PGQL where :name is a label, receive positional ? parameters
        boolean positional = !graphQueryBuilder.usesDollarParameters();
        return context -> buildMatchInfo(context, query, positional);
    }

    private MethodMatchInfo buildMatchInfo(MethodMatchContext matchContext, String query, boolean positional) {
        ParameterElement[] parameters = matchContext.getParameters();
        ParameterElement entityParameter = null;
        ParameterElement entitiesParameter = null;
        if (parameters.length == 1) {
            entityParameter = Arrays.stream(parameters).filter(p -> TypeUtils.isEntity(p.getGenericType())).findFirst().orElse(null);
            entitiesParameter = Arrays.stream(parameters).filter(p -> TypeUtils.isIterableOfEntity(p.getGenericType())).findFirst().orElse(null);
        }
        boolean readOnly = matchContext.getAnnotationMetadata().booleanValue(Query.class, "readOnly").orElse(true);
        DataMethod.OperationType operationType = findOperationType(matchContext, query, readOnly);
        if (operationType == DataMethod.OperationType.QUERY) {
            entityParameter = null;
            entitiesParameter = null;
        } else if (!positional) {
            // SQL and PGQL statements report their row count through JDBC
            query = countingQuery(matchContext, query);
        }

        FindersUtils.InterceptorMatch entry = FindersUtils.resolveInterceptorTypeByOperationType(
            entityParameter != null,
            entitiesParameter != null,
            operationType,
            matchContext);
        ClassElement resultType = entry.returnType();
        boolean dto = operationType == DataMethod.OperationType.QUERY
            && resultType.hasStereotype(Introspected.class)
            && !resultType.hasStereotype(MappedEntity.class);

        MethodMatchInfo methodMatchInfo = new MethodMatchInfo(operationType, resultType, entry.interceptor());
        methodMatchInfo.dto(dto);

        SourcePersistentEntity persistentEntity = null;
        ParameterElement entityParam = null;
        if (entityParameter != null) {
            entityParam = entityParameter;
            persistentEntity = matchContext.getEntity(entityParameter.getGenericType());
        } else if (entitiesParameter != null) {
            entityParam = entitiesParameter;
            persistentEntity = matchContext.getEntity(entitiesParameter.getGenericType().getFirstTypeArgument().orElseThrow(IllegalStateException::new));
        }
        QueryResult queryResult = buildQueryResult(matchContext, query, entityParam, persistentEntity, positional);
        String countQuery = matchContext.getAnnotationMetadata().stringValue(Query.class, "countQuery").orElse(null);
        QueryResult countQueryResult = countQuery == null ? null : buildQueryResult(matchContext, countQuery, entityParam, persistentEntity, positional);
        methodMatchInfo
            .isRawQuery(true)
            .encodeEntityParameters(persistentEntity != null)
            .queryResult(queryResult)
            .countQueryResult(countQueryResult);
        if (entityParameter != null) {
            methodMatchInfo.addParameterRole(entityParameter, TypeRole.ENTITY);
        } else if (entitiesParameter != null) {
            methodMatchInfo.addParameterRole(entitiesParameter, TypeRole.ENTITIES);
        }
        return methodMatchInfo;
    }

    private static DataMethod.OperationType findOperationType(MethodMatchContext matchContext, String query, boolean readOnly) {
        String upper = stripLiterals(query).toUpperCase(Locale.ENGLISH);
        boolean returnsResults = RETURN_PATTERN.matcher(upper).find() || SELECT_PATTERN.matcher(upper).find();
        ClassElement returnType = TypeUtils.getMethodProducingItemType(matchContext.getMethodElement());
        boolean returnsCount = returnType == null || TypeUtils.isVoid(returnType) || TypeUtils.isNumber(returnType);
        boolean deletes = DELETE_PATTERN.matcher(upper).find();
        boolean writes = WRITE_PATTERN.matcher(upper).find();
        if (returnsResults && !(returnsCount && (deletes || writes))) {
            return DataMethod.OperationType.QUERY;
        }
        if (deletes) {
            return DataMethod.OperationType.DELETE;
        }
        if (writes || !readOnly) {
            return DataMethod.OperationType.UPDATE;
        }
        throw new MatchFailedException("A graph query must have a RETURN clause, or write with CREATE, MERGE, SET, REMOVE or DELETE");
    }

    /**
     * A write query whose method returns a number reports the number of rows it wrote. The query
     * returns that count itself, so every database reports the same number, rather than the sum of
     * the driver's counters, which differs between databases and counts properties rather than rows.
     *
     * @param matchContext The match context
     * @param query        The write query
     * @return The query, returning its row count when the method asks for one
     */
    private static String countingQuery(MethodMatchContext matchContext, String query) {
        ClassElement returnType = TypeUtils.getMethodProducingItemType(matchContext.getMethodElement());
        if (returnType == null || !TypeUtils.isNumber(returnType)) {
            return query;
        }
        String upper = stripLiterals(query).toUpperCase(Locale.ENGLISH);
        if (RETURN_PATTERN.matcher(upper).find()) {
            return query;
        }
        return query + " RETURN count(*)";
    }

    private static QueryResult buildQueryResult(MethodMatchContext matchContext,
                                                String query,
                                                @Nullable ParameterElement entityParam,
                                                @Nullable SourcePersistentEntity persistentEntity,
                                                boolean positional) {
        List<ParameterElement> parameters = Arrays.asList(matchContext.getParameters());
        List<AnnotationValue<ParameterExpression>> parameterExpressions = matchContext.getMethodElement()
            .getAnnotationMetadata()
            .getAnnotationValuesByType(ParameterExpression.class);
        List<QueryParameterBinding> bindings = new ArrayList<>();
        String scanned = maskLiterals(query, positional);
        Matcher matcher = PARAMETER_PATTERN.matcher(scanned);
        StringBuilder rewritten = new StringBuilder();
        int lastEnd = 0;
        int index = 0;
        while (matcher.find()) {
            String name = matcher.group(1);
            // Positional parameters are keyed by position, as a parameter may occur more than once
            String key = positional ? "p" + index : name;
            bindings.add(RawQueryMethodMatcher.addBinding(
                matchContext,
                parameters,
                parameterExpressions,
                entityParam,
                persistentEntity,
                name,
                BindingContext.create().index(index++).name(key)
            ));
            rewritten.append(query, lastEnd, matcher.start()).append('?');
            lastEnd = matcher.end();
        }
        String finalQuery = query;
        if (positional) {
            finalQuery = rewritten.append(query.substring(lastEnd)).toString();
        }
        if (matchContext.getQueryBuilder() instanceof GraphQueryBuilder graphQueryBuilder) {
            try {
                finalQuery = graphQueryBuilder.finishQuery(finalQuery);
            } catch (IllegalArgumentException e) {
                throw new MatchFailedException(String.valueOf(e.getMessage()));
            }
        }
        return QueryResult.of(finalQuery, List.of(finalQuery), bindings);
    }

    /**
     * Replaces the content of string literals and comments with spaces, keeping offsets.
     *
     * @param query The query
     * @return The masked query
     */
    static String maskLiterals(String query) {
        return maskLiterals(query, false);
    }

    /**
     * Replaces the content of string literals and comments with spaces, keeping offsets.
     *
     * @param query The query
     * @param sql   Whether the query is SQL or PGQL, where {@code --} opens a comment and a quote is
     *              doubled rather than escaped with a backslash
     * @return The masked query
     */
    static String maskLiterals(String query, boolean sql) {
        return new LiteralMasker(query, sql).mask();
    }

    private static String stripLiterals(String query) {
        String names = NAME_PATTERN.matcher(maskLiterals(query)).replaceAll(" ");
        return MAP_KEY_PATTERN.matcher(names).replaceAll(" ");
    }

    /**
     * Masks string literals and comments, keeping the offsets of everything else.
     */
    private static final class LiteralMasker {
        private final String query;
        private final boolean sql;
        private final StringBuilder out;
        private int position;
        private char quote;
        private boolean lineComment;
        private boolean blockComment;

        private LiteralMasker(String query, boolean sql) {
            this.query = query;
            this.sql = sql;
            this.out = new StringBuilder(query.length());
        }

        private String mask() {
            while (position < query.length()) {
                char c = query.charAt(position);
                char next = position + 1 < query.length() ? query.charAt(position + 1) : 0;
                if (lineComment) {
                    maskLineComment(c);
                } else if (blockComment) {
                    maskBlockComment(c, next);
                } else if (quote != 0) {
                    maskQuoted(c, next);
                } else {
                    scan(c, next);
                }
            }
            return out.toString();
        }

        private void maskLineComment(char c) {
            if (c == '\n') {
                lineComment = false;
                append(c);
            } else {
                append(' ');
            }
        }

        private void maskBlockComment(char c, char next) {
            if (c == '*' && next == '/') {
                blockComment = false;
                appendTwo("  ");
            } else {
                append(' ');
            }
        }

        private void maskQuoted(char c, char next) {
            if (c == '\\' && next != 0 && !sql) {
                appendTwo("  ");
            } else if (c == quote) {
                quote = 0;
                append(c);
            } else {
                append(' ');
            }
        }

        private void scan(char c, char next) {
            if (c == '\'' || c == '"' || c == '`') {
                quote = c;
                append(c);
            } else if ((c == '/' && next == '/') || (sql && c == '-' && next == '-')) {
                lineComment = true;
                appendTwo("  ");
            } else if (c == '/' && next == '*') {
                blockComment = true;
                appendTwo("  ");
            } else {
                append(c);
            }
        }

        private void append(char c) {
            out.append(c);
            position++;
        }

        private void appendTwo(String text) {
            out.append(text);
            position += 2;
        }
    }
}
