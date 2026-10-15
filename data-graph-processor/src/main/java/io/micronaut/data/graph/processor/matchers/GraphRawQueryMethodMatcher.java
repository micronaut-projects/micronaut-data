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
package io.micronaut.data.graph.processor.matchers;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.ParameterExpression;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.graph.annotation.GraphRepository;
import io.micronaut.data.graph.model.query.builder.CypherQueryBuilder;
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

    private static final Pattern PARAMETER_PATTERN = Pattern.compile("\\$([a-zA-Z_][a-zA-Z0-9_]*)");
    private static final Pattern WRITE_PATTERN = Pattern.compile("\\b(CREATE|MERGE|SET|REMOVE|INSERT|UPDATE)\\b");
    private static final Pattern DELETE_PATTERN = Pattern.compile("\\bDELETE\\b");
    private static final Pattern RETURN_PATTERN = Pattern.compile("\\bRETURN\\b");

    @Override
    public int getOrder() {
        // Before RawQueryMethodMatcher, which parses SQL
        return DEFAULT_POSITION - 2000;
    }

    @Override
    public @Nullable MethodMatch match(MethodMatchContext matchContext) {
        if (!matchContext.getRepositoryClass().hasStereotype(GraphRepository.class)) {
            return null;
        }
        String query = matchContext.getMethodElement().stringValue(Query.class).orElse(null);
        if (query == null) {
            return null;
        }
        return context -> buildMatchInfo(context, query);
    }

    private MethodMatchInfo buildMatchInfo(MethodMatchContext matchContext, String query) {
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
        QueryResult queryResult = buildQueryResult(matchContext, query, entityParam, persistentEntity);
        String countQuery = matchContext.getAnnotationMetadata().stringValue(Query.class, "countQuery").orElse(null);
        QueryResult countQueryResult = countQuery == null ? null : buildQueryResult(matchContext, countQuery, entityParam, persistentEntity);
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
        boolean returnsResults = RETURN_PATTERN.matcher(upper).find();
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
        if (!returnsResults) {
            throw new MatchFailedException("A graph query must have a RETURN clause, or write with CREATE, MERGE, SET, REMOVE or DELETE");
        }
        return DataMethod.OperationType.QUERY;
    }

    private static QueryResult buildQueryResult(MethodMatchContext matchContext,
                                                String query,
                                                @Nullable ParameterElement entityParam,
                                                @Nullable SourcePersistentEntity persistentEntity) {
        List<ParameterElement> parameters = Arrays.asList(matchContext.getParameters());
        List<AnnotationValue<ParameterExpression>> parameterExpressions = matchContext.getMethodElement()
            .getAnnotationMetadata()
            .getAnnotationValuesByType(ParameterExpression.class);
        List<QueryParameterBinding> bindings = new ArrayList<>();
        String scanned = maskLiterals(query);
        Matcher matcher = PARAMETER_PATTERN.matcher(scanned);
        int index = 0;
        while (matcher.find()) {
            String name = matcher.group(1);
            bindings.add(RawQueryMethodMatcher.addBinding(
                matchContext,
                parameters,
                parameterExpressions,
                entityParam,
                persistentEntity,
                name,
                BindingContext.create().index(index++).name(name)
            ));
        }
        String finalQuery = query;
        if (matchContext.getQueryBuilder() instanceof CypherQueryBuilder cypherQueryBuilder) {
            try {
                finalQuery = cypherQueryBuilder.finishQuery(query);
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
        StringBuilder sb = new StringBuilder(query.length());
        char quote = 0;
        boolean lineComment = false;
        boolean blockComment = false;
        for (int i = 0; i < query.length(); i++) {
            char c = query.charAt(i);
            char next = i + 1 < query.length() ? query.charAt(i + 1) : 0;
            if (lineComment) {
                if (c == '\n') {
                    lineComment = false;
                    sb.append(c);
                } else {
                    sb.append(' ');
                }
            } else if (blockComment) {
                if (c == '*' && next == '/') {
                    blockComment = false;
                    sb.append("  ");
                    i++;
                } else {
                    sb.append(' ');
                }
            } else if (quote != 0) {
                if (c == '\\' && next != 0) {
                    sb.append("  ");
                    i++;
                } else {
                    if (c == quote) {
                        quote = 0;
                    }
                    sb.append(c == quote || quote == 0 ? c : ' ');
                }
            } else if (c == '\'' || c == '"' || c == '`') {
                quote = c;
                sb.append(c);
            } else if (c == '/' && next == '/') {
                lineComment = true;
                sb.append("  ");
                i++;
            } else if (c == '/' && next == '*') {
                blockComment = true;
                sb.append("  ");
                i++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String stripLiterals(String query) {
        return maskLiterals(query);
    }
}
