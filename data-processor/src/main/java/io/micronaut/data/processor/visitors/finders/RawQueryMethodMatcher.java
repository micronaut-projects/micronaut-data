/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.data.processor.visitors.finders;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.expressions.EvaluatedExpressionReference;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.ParameterExpression;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.RepositoryConfiguration;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.query.BindingParameter.BindingContext;
import io.micronaut.data.model.query.builder.QueryOutParameterBinding;
import io.micronaut.data.model.query.builder.QueryParameterBinding;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.criteria.impl.SourceParameterExpressionImpl;
import io.micronaut.data.processor.visitors.MatchContext;
import io.micronaut.data.processor.visitors.MatchFailedException;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.data.processor.visitors.Utils;
import io.micronaut.expressions.context.DefaultExpressionCompilationContextFactory;
import io.micronaut.expressions.context.ExpressionEvaluationContext;
import io.micronaut.expressions.parser.CompoundEvaluatedExpressionParser;
import io.micronaut.expressions.parser.compilation.ExpressionVisitorContext;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.processing.ProcessingException;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finder with custom defied query used to return a single result.
 *
 * @author Denis Stepanov
 * @since 2.4.0
 */
public class RawQueryMethodMatcher implements MethodMatcher {

    private static final Pattern SQL_COMMENT_PATTERN = Pattern.compile("(--[^\\r\\n]*)|(/\\*[\\s\\S]*?\\*/)", Pattern.MULTILINE);

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("([^:\\\\]*)((?<![:]):([a-zA-Z0-9]+))([^:]*)");
    private static final String COLON = ":";
    private static final String COLON_ESCAPE_PATTERN = "\\" + COLON;
    private static final String COLON_TEMP_REPLACEMENT = "___MICRONAUT_COLON_PLA@CEHOLDER___";

    @Override
    public final int getOrder() {
        // should run first
        return DEFAULT_POSITION - 1000;
    }

    @Override
    @Nullable
    public MethodMatch match(MethodMatchContext matchContext) {
        if (matchContext.getMethodElement().stringValue(Query.class).isPresent()) {
            return new MethodMatch() {

                @Override
                public MethodMatchInfo buildMatchInfo(MethodMatchContext matchContext) {
                    boolean implicitQueries = matchContext.getRepositoryClass().booleanValue(RepositoryConfiguration.class, "implicitQueries").orElse(true);

                    MethodElement methodElement = matchContext.getMethodElement();

                    ParameterElement[] parameters = matchContext.getParameters();
                    ParameterElement entityParameter;
                    ParameterElement entitiesParameter;
                    if (parameters.length > 1) {
                        entityParameter = null;
                        entitiesParameter = null;
                    } else {
                        entityParameter = Arrays.stream(parameters).filter(p -> TypeUtils.isEntity(p.getGenericType())).findFirst().orElse(null);
                        entitiesParameter = Arrays.stream(parameters).filter(p -> TypeUtils.isIterableOfEntity(p.getGenericType())).findFirst().orElse(null);
                    }

                    boolean readOnly = matchContext.getAnnotationMetadata().booleanValue(Query.class, "readOnly").orElse(true);
                    String query = matchContext.getAnnotationMetadata().stringValue(Query.class).orElseThrow(IllegalStateException::new);
                    DataMethod.OperationType operationType = findOperationType(methodElement.getName(), query, readOnly);

// Don't use implicit entity interceptors for implicit-query repositories
                    // Otherwise JPA's implicit interceptors will not use a custom query
                    FindersUtils.InterceptorMatch entry = FindersUtils.resolveInterceptorTypeByOperationType(
                        entityParameter != null,
                        entitiesParameter != null,
                        operationType,
                        matchContext);
                    ClassElement resultType = entry.returnType();
                    ClassElement interceptorType = entry.interceptor();

                    if (entityParameter == null
                        && entitiesParameter == null
                        && operationType == DataMethod.OperationType.INSERT
                        && (interceptorType.getSimpleName().startsWith("SaveOne") || interceptorType.getSimpleName().startsWith("InsertOne"))) {
                        // Use `executeUpdate` operation for "insert(String a, String b)" style queries
                        // - custom query doesn't need to use root entity
                        // - we would like to know how many rows were updated
                        FindersUtils.InterceptorMatch e = FindersUtils.pickUpdateInterceptor(matchContext, matchContext.getReturnType());
                        resultType = e.returnType();
                        interceptorType = e.interceptor();
                        operationType = DataMethod.OperationType.UPDATE;
                    }

                    if (operationType == DataMethod.OperationType.QUERY) {
                        // Entity parameter/parameters only make sense if the operation is based on entity
                        entityParameter = null;
                        entitiesParameter = null;
                    }

                    boolean isDto = false;
                    if (resultType == null) {
                        resultType = matchContext.getRootEntity().getType();
                    } else {
                        if (operationType == DataMethod.OperationType.QUERY) {
                            if (resultType.hasStereotype(Introspected.class)) {
                                if (!resultType.hasStereotype(MappedEntity.class)) {
                                    isDto = true;
                                }
                            }
                        } else if (!isValidReturnType(resultType, operationType)) {
                            throw new MatchFailedException("Invalid result type: " + resultType.getName() + " for '" + operationType + "' operation");
                        }
                    }

                    MethodMatchInfo methodMatchInfo = new MethodMatchInfo(
                        operationType,
                        resultType,
                        interceptorType
                    );

                    methodMatchInfo.dto(isDto);

                    buildRawQuery(matchContext, methodMatchInfo, entityParameter, entitiesParameter, operationType, implicitQueries);

                    if (entityParameter != null) {
                        methodMatchInfo.addParameterRole(entityParameter, TypeRole.ENTITY);
                    } else if (entitiesParameter != null) {
                        methodMatchInfo.addParameterRole(entitiesParameter, TypeRole.ENTITIES);
                    }
                    return methodMatchInfo;
                }
            };
        }
        return null;
    }

    private boolean isValidReturnType(ClassElement returnType, DataMethod.OperationType operationType) {
        if (operationType == DataMethod.OperationType.INSERT) {
            return TypeUtils.isVoid(returnType) || TypeUtils.isNumber(returnType);
        }
        return true;
    }

    private DataMethod.OperationType findOperationType(String methodName, String query, boolean readOnly) {
        query = query.trim().toLowerCase(Locale.ENGLISH);
        query = SQL_COMMENT_PATTERN.matcher(query).replaceAll("").trim();

        SqlStatement statement = findSqlStatement(query);
        if (statement == SqlStatement.DELETE) {
            if (containsReturningClause(query)) {
                return DataMethod.OperationType.DELETE_RETURNING;
            }
            return DataMethod.OperationType.DELETE;
        } else if (statement == SqlStatement.INSERT) {
            if (containsReturningClause(query)) {
                return DataMethod.OperationType.INSERT_RETURNING;
            }
            return DataMethod.OperationType.INSERT;
        } else if (statement == SqlStatement.REPLACE) {
            if (containsReturningClause(query)) {
                return DataMethod.OperationType.UPDATE_RETURNING;
            }
            return DataMethod.OperationType.UPDATE;
        } else if (statement == SqlStatement.UPDATE) {
            if (containsReturningClause(query)) {
                return DataMethod.OperationType.UPDATE_RETURNING;
            }
            if (DeleteMethodMatcher.METHOD_PATTERN.matcher(methodName.toLowerCase(Locale.ENGLISH)).matches()) {
                return DataMethod.OperationType.DELETE;
            }
            return DataMethod.OperationType.UPDATE;
        }
        if (readOnly) {
            return DataMethod.OperationType.QUERY;
        }
        return DataMethod.OperationType.UPDATE;
    }

    /**
     * Builds a raw query for the given match context. Should be called for methods annotated with {@link Query} explicitly.
     */
    private void buildRawQuery(MethodMatchContext matchContext,
                               MethodMatchInfo methodMatchInfo,
                               @Nullable
                               ParameterElement entityParameter,
                               @Nullable
                               ParameterElement entitiesParameter,
                               DataMethod.OperationType operationType,
                               boolean implicitQueries) {
        MethodElement methodElement = matchContext.getMethodElement();
        String queryString = methodElement.stringValue(Query.class).orElseThrow(() ->
            new IllegalStateException("Should only be called if Query has value!")
        );
        List<ParameterElement> parameters = Arrays.asList(matchContext.getParameters());
        boolean namedParameters = matchContext.getRepositoryClass()
            .booleanValue(RepositoryConfiguration.class, "namedParameters").orElse(true);

        ParameterElement entityParam = null;
        SourcePersistentEntity persistentEntity = null;
        if (entityParameter != null) {
            entityParam = entityParameter;
            persistentEntity = matchContext.getEntity(entityParameter.getGenericType());
        } else if (entitiesParameter != null) {
            entityParam = entitiesParameter;
            persistentEntity = matchContext.getEntity(entitiesParameter.getGenericType().getFirstTypeArgument().orElseThrow(IllegalStateException::new));
        }

        QueryResult queryResult = getQueryResult(
            matchContext,
            queryString,
            parameters,
            namedParameters,
            entityParam,
            persistentEntity,
            methodMatchInfo.getResultType(),
            operationType
        );
        String cq = matchContext.getAnnotationMetadata().stringValue(Query.class, "countQuery")
            .orElse(null);
        QueryResult countQueryResult = cq == null ? null : getQueryResult(
            matchContext,
            cq,
            parameters,
            namedParameters,
            entityParam,
            persistentEntity,
            methodMatchInfo.getResultType(),
            DataMethod.OperationType.QUERY
        );
        boolean encodeEntityParameters;
        if (implicitQueries) {
            encodeEntityParameters = persistentEntity != null || operationType == DataMethod.OperationType.INSERT;
        } else {
            encodeEntityParameters = false;
        }
        methodMatchInfo
            .isRawQuery(true)
            .encodeEntityParameters(encodeEntityParameters)
            .queryResult(queryResult)
            .countQueryResult(countQueryResult);
    }

    private QueryResult getQueryResult(MethodMatchContext matchContext,
                                       String queryString,
                                       List<ParameterElement> parameters,
                                       boolean namedParameters,
                                       @Nullable
                                       ParameterElement entityParam,
                                       @Nullable
                                       SourcePersistentEntity persistentEntity,
                                       @Nullable
                                       TypedElement resultType,
                                       DataMethod.OperationType operationType) {
        String newQueryString = queryString.replace(COLON_ESCAPE_PATTERN, COLON_TEMP_REPLACEMENT);
        Matcher matcher = VARIABLE_PATTERN.matcher(newQueryString);

        List<AnnotationValue<ParameterExpression>> parameterExpressions = matchContext.getMethodElement()
            .getAnnotationMetadata()
            .getAnnotationValuesByType(ParameterExpression.class);

        List<QueryParameterBinding> parameterBindings = new ArrayList<>(parameters.size());
        List<String> queryParts = new ArrayList<>();
        int index = 1;
        int lastOffset = 0;
        while (matcher.find()) {
            String prefix = newQueryString.substring(lastOffset, matcher.start(3) - 1);
            if (!prefix.isEmpty()) {
                queryParts.add(prefix.replace(COLON_TEMP_REPLACEMENT, COLON));
            }
            lastOffset = matcher.end(3);
            String name = matcher.group(3);
            BindingContext bindingContext;
            if (namedParameters) {
                bindingContext = BindingContext.create().name(name);
            } else {
                bindingContext = BindingContext.create().index(index++);
            }
            QueryParameterBinding queryParameterBinding = addBinding(
                matchContext,
                parameters,
                parameterExpressions,
                entityParam,
                persistentEntity,
                name,
                bindingContext);
            parameterBindings.add(queryParameterBinding);
        }

        if (queryParts.isEmpty()) {
            queryParts.add(newQueryString.replace(COLON_TEMP_REPLACEMENT, COLON));
        } else if (lastOffset > 0) {
            queryParts.add(newQueryString.substring(lastOffset).replace(COLON_TEMP_REPLACEMENT, COLON));
        }
        String finalQueryString = newQueryString.replace(COLON_TEMP_REPLACEMENT, COLON);

        if (isReturningOperation(operationType)) {
            Dialect dialect = matchContext.getRepositoryClass().enumValue(Repository.class, "dialect", Dialect.class)
                .orElse(Dialect.ANSI);
            if (dialect == Dialect.ORACLE) {
                SourcePersistentEntity entity = persistentEntity != null ? persistentEntity : matchContext.getRootEntity();
                return OracleRawQueryReturningSupport.buildQueryResult(
                    finalQueryString,
                    queryParts,
                    parameterBindings,
                    entity,
                    resultType,
                    RawQueryMethodMatcher::createOutBinding
                );
            }
        }

        // Default: no transformation
        return QueryResult.of(finalQueryString, queryParts, parameterBindings);
    }

    private static boolean isReturningOperation(DataMethod.OperationType operationType) {
        return operationType == DataMethod.OperationType.INSERT_RETURNING
            || operationType == DataMethod.OperationType.UPDATE_RETURNING
            || operationType == DataMethod.OperationType.DELETE_RETURNING;
    }

    private static boolean containsReturningClause(String query) {
        return containsTopLevelKeywordOutsideQuotes(query, "returning");
    }

    private static boolean containsTopLevelKeywordOutsideQuotes(String query, String keyword) {
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        int depth = 0;
        for (int i = 0; i < query.length(); i++) {
            char c = query.charAt(i);
            if (inSingleQuote) {
                if (c == '\'' && (i + 1 >= query.length() || query.charAt(i + 1) != '\'')) {
                    inSingleQuote = false;
                } else if (c == '\'' && i + 1 < query.length() && query.charAt(i + 1) == '\'') {
                    i++;
                }
                continue;
            }
            if (inDoubleQuote) {
                if (c == '"' && (i + 1 >= query.length() || query.charAt(i + 1) != '"')) {
                    inDoubleQuote = false;
                } else if (c == '"' && i + 1 < query.length() && query.charAt(i + 1) == '"') {
                    i++;
                }
                continue;
            }
            if (c == '\'') {
                inSingleQuote = true;
                continue;
            }
            if (c == '"') {
                inDoubleQuote = true;
                continue;
            }
            if (c == '(') {
                depth++;
                continue;
            }
            if (c == ')') {
                if (depth > 0) {
                    depth--;
                }
                continue;
            }
            if (depth > 0) {
                continue;
            }
            if (isKeywordAt(query, keyword, i)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isKeywordBoundary(String query, int index) {
        if (index < 0 || index >= query.length()) {
            return true;
        }
        char c = query.charAt(index);
        return !Character.isLetterOrDigit(c) && c != '_';
    }

    private static SqlStatement findSqlStatement(String query) {
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        int depth = 0;
        for (int i = 0; i < query.length(); i++) {
            char c = query.charAt(i);
            if (inSingleQuote) {
                if (c == '\'' && (i + 1 >= query.length() || query.charAt(i + 1) != '\'')) {
                    inSingleQuote = false;
                } else if (c == '\'' && i + 1 < query.length() && query.charAt(i + 1) == '\'') {
                    i++;
                }
                continue;
            }
            if (inDoubleQuote) {
                if (c == '"' && (i + 1 >= query.length() || query.charAt(i + 1) != '"')) {
                    inDoubleQuote = false;
                } else if (c == '"' && i + 1 < query.length() && query.charAt(i + 1) == '"') {
                    i++;
                }
                continue;
            }
            if (c == '\'') {
                inSingleQuote = true;
                continue;
            }
            if (c == '"') {
                inDoubleQuote = true;
                continue;
            }
            if (c == '(') {
                depth++;
                continue;
            }
            if (c == ')') {
                if (depth > 0) {
                    depth--;
                }
                continue;
            }
            if (depth > 0) {
                continue;
            }
            if (isKeywordAt(query, "select", i)) {
                return SqlStatement.QUERY;
            }
            if (isKeywordAt(query, "delete", i)) {
                return SqlStatement.DELETE;
            }
            if (isKeywordAt(query, "insert", i)) {
                return SqlStatement.INSERT;
            }
            if (isKeywordAt(query, "update", i)) {
                return SqlStatement.UPDATE;
            }
            if (isKeywordAt(query, "replace", i) && isNextKeyword(query, i + "replace".length(), "into")) {
                return SqlStatement.REPLACE;
            }
        }
        return SqlStatement.UNKNOWN;
    }

    private static boolean isKeywordAt(String query, String keyword, int index) {
        return query.regionMatches(true, index, keyword, 0, keyword.length())
            && isKeywordBoundary(query, index - 1)
            && isKeywordBoundary(query, index + keyword.length());
    }

    private static boolean isNextKeyword(String query, int index, String keyword) {
        int i = index;
        while (i < query.length() && Character.isWhitespace(query.charAt(i))) {
            i++;
        }
        return isKeywordAt(query, keyword, i);
    }

    private enum SqlStatement {
        QUERY,
        DELETE,
        INSERT,
        UPDATE,
        REPLACE,
        UNKNOWN
    }

    private static QueryOutParameterBinding createOutBinding(String column, DataType dataType) {
        return new QueryOutParameterBinding() {
            @Override
            public String getName() {
                return column;
            }

            @Override
            public DataType getDataType() {
                return dataType;
            }
        };
    }

    public static QueryParameterBinding addBinding(MethodMatchContext matchContext,
                                                   List<ParameterElement> parameters,
                                                   List<AnnotationValue<ParameterExpression>> parameterExpressions,
                                                   @Nullable
                                                   ParameterElement entityParam,
                                                   @Nullable
                                                   SourcePersistentEntity persistentEntity,
                                                   String name,
                                                   BindingContext bindingContext) {
        Optional<AnnotationValue<ParameterExpression>> parameterExpression = parameterExpressions.stream()
            .filter(av -> av.stringValue("name").orElse("").equals(name))
            .findFirst();
        if (parameterExpression.isPresent()) {
            ClassElement type = extractExpressionType(matchContext, parameterExpression.orElseThrow());

            PersistentPropertyPath propertyPath = matchContext.getRootEntity().getPropertyPath(name);
            bindingContext = bindingContext
                .incomingMethodParameterProperty(propertyPath)
                .outgoingQueryParameterProperty(propertyPath);

            return bindingParameter(matchContext, name, type)
                .bind(bindingContext);
        }
        Optional<ParameterElement> element = parameters.stream()
            .filter(p -> p.stringValue(Parameter.class).orElse(p.getName()).equals(name))
            .findFirst();
        if (element.isPresent()) {
            PersistentPropertyPath propertyPath = !matchContext.hasRootEntity() ? null : matchContext.getRootEntity().getPropertyPath(name);
            bindingContext = bindingContext
                .incomingMethodParameterProperty(propertyPath)
                .outgoingQueryParameterProperty(propertyPath);
            return bindingParameter(matchContext, element.get())
                .bind(bindingContext);
        }
        if (persistentEntity != null) {
            PersistentPropertyPath propertyPath = persistentEntity.getPropertyPath(name);
            if (propertyPath == null) {
                throw new MatchFailedException("Cannot update non-existent property: " + name);
            } else {
                bindingContext = bindingContext
                    .incomingMethodParameterProperty(propertyPath)
                    .outgoingQueryParameterProperty(propertyPath);
                return bindingParameter(matchContext, entityParam, true)
                    .bind(bindingContext);
            }
        }
        throw new MatchFailedException("No method parameter found for named Query parameter: " + name);
    }

    private static SourceParameterExpressionImpl bindingParameter(MethodMatchContext matchContext, @Nullable ParameterElement element) {
        return bindingParameter(matchContext, element, false);
    }

    private static SourceParameterExpressionImpl bindingParameter(MethodMatchContext matchContext, @Nullable ParameterElement element, boolean isEntityParameter) {
        return new SourceParameterExpressionImpl(
            Utils.getConfiguredDataTypes(matchContext.getRepositoryClass()),
            matchContext.getParameters(),
            element,
            isEntityParameter,
            null);
    }

    private static SourceParameterExpressionImpl bindingParameter(MethodMatchContext matchContext,
                                                                  String name,
                                                                  @Nullable ClassElement type) {
        return new SourceParameterExpressionImpl(
            Utils.getConfiguredDataTypes(matchContext.getRepositoryClass()),
            name,
            type,
            null);
    }

    /**
     * Extract the expression type.
     *
     * @param matchContext        The match context
     * @param parameterExpression The parameter expression
     * @return the type
     */
    @Nullable
    public static ClassElement extractExpressionType(MatchContext matchContext, AnnotationValue<ParameterExpression> parameterExpression) {
        Object expressionValue = parameterExpression.getValues().get("expression");
        if (expressionValue == null) {
            return null;
        }
        if (expressionValue instanceof String) {
            throw new ProcessingException(matchContext.getMethodElement(), "Expected an expression '#{...}' found a string!");
        }
        EvaluatedExpressionReference ref = (EvaluatedExpressionReference) expressionValue;
        DefaultExpressionCompilationContextFactory factory = new DefaultExpressionCompilationContextFactory(matchContext.getVisitorContext());
        ExpressionEvaluationContext compilationContext = factory.buildContextForMethod(ref, matchContext.getMethodElement());
        String expression = (String) ref.annotationValue();
        return new CompoundEvaluatedExpressionParser(expression)
            .parse()
            .resolveClassElement(new ExpressionVisitorContext(compilationContext, matchContext.getVisitorContext()));
    }

}
