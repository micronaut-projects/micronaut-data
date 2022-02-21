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
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.RepositoryConfiguration;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.intercept.DataInterceptor;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.query.BindingParameter.BindingContext;
import io.micronaut.data.model.query.builder.QueryParameterBinding;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.criteria.impl.SourceParameterExpressionImpl;
import io.micronaut.data.processor.visitors.MatchFailedException;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.data.processor.visitors.Utils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Finder with custom defied query used to return a single result.
 *
 * @author Denis Stepanov
 * @since 2.4.0
 */
public class RawQueryMethodMatcher implements MethodMatcher {

    private static final String SELECT = "select";
    private static final String DELETE = "delete";
    private static final String UPDATE = "update";
    private static final String INSERT = "insert";

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("([^:]*)((?<![:]):([a-zA-Z0-9]+))([^:]*)");

    /**
     * Default constructor.
     */
    public RawQueryMethodMatcher() {
    }

    @Override
    public final int getOrder() {
        // should run first
        return DEFAULT_POSITION - 1000;
    }

    @Override
    public MethodMatch match(MethodMatchContext matchContext) {
        if (matchContext.getMethodElement().stringValue(Query.class).isPresent()) {
            return new MethodMatch() {

                @Override
                public MethodMatchInfo buildMatchInfo(MethodMatchContext matchContext) {
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
                    String query = matchContext.getAnnotationMetadata().stringValue(Query.class).get();
                    DataMethod.OperationType operationType = findOperationType(methodElement.getName(), query, readOnly);

                    Map.Entry<ClassElement, Class<? extends DataInterceptor>> entry = FindersUtils.resolveInterceptorTypeByOperationType(
                            entityParameter != null,
                            entitiesParameter != null,
                            operationType,
                            matchContext);
                    ClassElement resultType = entry.getKey();
                    Class<? extends DataInterceptor> interceptorType = entry.getValue();

                    if (interceptorType.getSimpleName().startsWith("SaveOne")) {
                        // Use `executeUpdate` operation for "insert(String a, String b)" style queries
                        // - custom query doesn't need to use root entity
                        // - we would like to know how many rows were updated
                        operationType = DataMethod.OperationType.UPDATE;
                        Map.Entry<ClassElement, Class<? extends DataInterceptor>> e = FindersUtils.pickUpdateInterceptor(matchContext, matchContext.getReturnType());
                        resultType = e.getKey();
                        interceptorType = e.getValue();
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
                            if (resultType.hasAnnotation(Introspected.class)) {
                                if (!resultType.hasAnnotation(MappedEntity.class)) {
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
                            FindersUtils.getInterceptorElement(matchContext, interceptorType)
                    );

                    methodMatchInfo.dto(isDto);

                    buildRawQuery(matchContext, methodMatchInfo, entityParameter, entitiesParameter, operationType);

                    if (entityParameter != null) {
                        methodMatchInfo.addParameterRole(TypeRole.ENTITY, entityParameter.getName());
                    } else if (entitiesParameter != null) {
                        methodMatchInfo.addParameterRole(TypeRole.ENTITIES, entitiesParameter.getName());
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
        if (query.startsWith(SELECT)) {
            return DataMethod.OperationType.QUERY;
        } else if (query.startsWith(DELETE)) {
            return DataMethod.OperationType.DELETE;
        } else if (query.startsWith(UPDATE)) {
            if (DeleteMethodMatcher.METHOD_PATTERN.matcher(methodName.toLowerCase(Locale.ENGLISH)).matches()) {
                return DataMethod.OperationType.DELETE;
            }
            return DataMethod.OperationType.UPDATE;
        } else if (query.startsWith(INSERT)) {
            return DataMethod.OperationType.INSERT;
        }
        if (readOnly) {
            return DataMethod.OperationType.QUERY;
        }
        return DataMethod.OperationType.UPDATE;
    }

    /**
     * Builds a raw query for the given match context. Should be called for methods annotated with {@link Query} explicitly.
     */
    private void buildRawQuery(@NonNull MethodMatchContext matchContext,
                               MethodMatchInfo methodMatchInfo,
                               ParameterElement entityParameter,
                               ParameterElement entitiesParameter,
                               DataMethod.OperationType operationType) {
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
            persistentEntity = matchContext.getEntity(entitiesParameter.getGenericType().getFirstTypeArgument().get());
        }

        QueryResult queryResult = getQueryResult(matchContext, queryString, parameters, namedParameters, entityParam, persistentEntity);
        String cq = matchContext.getAnnotationMetadata().stringValue(Query.class, "countQuery")
                .orElse(null);
        QueryResult countQueryResult = cq == null ? null : getQueryResult(matchContext, cq, parameters, namedParameters, entityParam, persistentEntity);
        boolean encodeEntityParameters = persistentEntity != null || operationType == DataMethod.OperationType.INSERT;
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
                                       ParameterElement entityParam,
                                       SourcePersistentEntity persistentEntity) {
        java.util.regex.Matcher matcher = VARIABLE_PATTERN.matcher(queryString);
        List<QueryParameterBinding> parameterBindings = new ArrayList<>(parameters.size());
        List<String> queryParts = new ArrayList<>();
        boolean requiresEnd = true;
        int index = 1;
        while (matcher.find()) {
            requiresEnd = true;
            String start = matcher.group(1);
            if (!start.isEmpty()) {
                queryParts.add(start);
            }
            String end = matcher.group(4);
            if (!end.isEmpty()) {
                requiresEnd = false;
                queryParts.add(end);
            }
            String name = matcher.group(3);
            if (namedParameters) {
                Optional<ParameterElement> element = parameters.stream()
                        .filter(p -> p.stringValue(Parameter.class).orElse(p.getName()).equals(name))
                        .findFirst();
                if (element.isPresent()) {
                    PersistentPropertyPath propertyPath = matchContext.getRootEntity().getPropertyPath(name);
                    BindingContext bindingContext = BindingContext.create()
                            .name(name)
                            .incomingMethodParameterProperty(propertyPath)
                            .outgoingQueryParameterProperty(propertyPath);
                    parameterBindings.add(bindingParameter(matchContext, element.get()).bind(bindingContext));
                } else if (persistentEntity != null) {
                    PersistentPropertyPath propertyPath = persistentEntity.getPropertyPath(name);
                    if (propertyPath == null) {
                        throw new MatchFailedException("Cannot update non-existent property: " + name);
                    } else {
                        BindingContext bindingContext = BindingContext.create()
                                .name(name)
                                .incomingMethodParameterProperty(propertyPath)
                                .outgoingQueryParameterProperty(propertyPath);
                        parameterBindings.add(bindingParameter(matchContext, entityParam, true).bind(bindingContext));
                    }
                } else {
                    throw new MatchFailedException("No method parameter found for named Query parameter: " + name);
                }
            } else {
                Optional<ParameterElement> element = parameters.stream()
                        .filter(p -> p.stringValue(Parameter.class).orElse(p.getName()).equals(name))
                        .findFirst();
                if (element.isPresent()) {
                    PersistentPropertyPath propertyPath = matchContext.getRootEntity().getPropertyPath(name);
                    BindingContext bindingContext = BindingContext.create()
                            .index(index++)
                            .incomingMethodParameterProperty(propertyPath)
                            .outgoingQueryParameterProperty(propertyPath);
                    parameterBindings.add(bindingParameter(matchContext, element.get()).bind(bindingContext));
                } else if (persistentEntity != null) {
                    PersistentPropertyPath propertyPath = persistentEntity.getPropertyPath(name);
                    if (propertyPath == null) {
                        matchContext.fail("Cannot update non-existent property: " + name);
                    } else {
                        BindingContext bindingContext = BindingContext.create()
                                .index(index++)
                                .incomingMethodParameterProperty(propertyPath)
                                .outgoingQueryParameterProperty(propertyPath);
                        parameterBindings.add(bindingParameter(matchContext, entityParam, true).bind(bindingContext));
                    }
                } else {
                    throw new MatchFailedException("No method parameter found for named Query parameter: " + name);
                }
            }
        }
        if (queryParts.isEmpty()) {
            queryParts.add(queryString);
        } else if (requiresEnd) {
            queryParts.add("");
        }
        return new QueryResult() {
            @Override
            public String getQuery() {
                return queryString;
            }

            @Override
            public List<String> getQueryParts() {
                return queryParts;
            }

            @Override
            public List<QueryParameterBinding> getParameterBindings() {
                return parameterBindings;
            }

            @Override
            public Map<String, String> getAdditionalRequiredParameters() {
                return Collections.emptyMap();
            }
        };
    }

    private SourceParameterExpressionImpl bindingParameter(MethodMatchContext matchContext, ParameterElement element) {
        return bindingParameter(matchContext, element, false);
    }

    private SourceParameterExpressionImpl bindingParameter(MethodMatchContext matchContext, ParameterElement element, boolean isEntityParameter) {
        return new SourceParameterExpressionImpl(
                Utils.getConfiguredDataTypes(matchContext.getRepositoryClass()),
                matchContext.getParameters(),
                element,
                isEntityParameter);
    }

}
