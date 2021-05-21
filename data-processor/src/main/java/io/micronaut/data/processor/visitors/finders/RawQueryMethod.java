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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.RepositoryConfiguration;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.intercept.DataInterceptor;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.query.QueryParameter;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryParameterBinding;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.visitors.MatchContext;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;

/**
 * Finder with custom defied query used to return a single result.
 *
 * @author Denis Stepanov
 * @since 2.4.0
 */
public class RawQueryMethod implements MethodCandidate {

    private static final String SELECT = "select";
    private static final String DELETE = "delete";
    private static final String UPDATE = "update";
    private static final String INSERT = "insert";

    /**
     * Default constructor.
     */
    public RawQueryMethod() {
    }

    @Override
    public final int getOrder() {
        // should run first
        return DEFAULT_POSITION - 1000;
    }

    @Override
    public boolean isMethodMatch(MethodElement methodElement, MatchContext matchContext) {
        return methodElement.stringValue(Query.class).isPresent();
    }

    @Nullable
    @Override
    public MethodMatchInfo buildMatchInfo(@NonNull MethodMatchContext matchContext) {
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
        MethodMatchInfo.OperationType operationType = findOperationType(methodElement.getName(), query, readOnly);

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
            operationType = MethodMatchInfo.OperationType.UPDATE;
            Map.Entry<ClassElement, Class<? extends DataInterceptor>> e = FindersUtils.pickUpdateInterceptor(matchContext, matchContext.getReturnType());
            resultType = e.getKey();
            interceptorType = e.getValue();
        }

        if (operationType == MethodMatchInfo.OperationType.QUERY) {
            // Entity parameter/parameters only make sense if the operation is based on entity
            entityParameter = null;
            entitiesParameter = null;
        }

        RawQuery queryModel = buildRawQuery(matchContext, entityParameter, entitiesParameter, operationType);
        if (queryModel == null) {
            return null;
        }

        boolean isDto = false;
        if (resultType == null) {
            resultType = matchContext.getRootEntity().getType();
        } else {
            if (operationType == MethodMatchInfo.OperationType.QUERY) {
                if (resultType.hasAnnotation(Introspected.class)) {
                    if (!resultType.hasAnnotation(MappedEntity.class)) {
                        isDto = true;
                    }
                }
            } else if (!isValidReturnType(resultType, operationType)) {
                matchContext.failAndThrow("Invalid result type: " + resultType.getName() + " for '" + operationType + "' operation");
                return null;
            }
        }

        MethodMatchInfo methodMatchInfo;
        if (isDto) {
            methodMatchInfo = new MethodMatchInfo(
                    resultType,
                    queryModel,
                    FindersUtils.getInterceptorElement(matchContext, interceptorType),
                    true);

        } else {
            methodMatchInfo = new MethodMatchInfo(
                    resultType,
                    queryModel,
                    FindersUtils.getInterceptorElement(matchContext, interceptorType),
                    operationType);
        }
        if (entityParameter != null) {
            methodMatchInfo.addParameterRole(TypeRole.ENTITY, entityParameter.getName());
        } else if (entitiesParameter != null) {
            methodMatchInfo.addParameterRole(TypeRole.ENTITIES, entitiesParameter.getName());
        }
        return methodMatchInfo;
    }

    private boolean isValidReturnType(ClassElement returnType, MethodMatchInfo.OperationType operationType) {
        if (operationType == MethodMatchInfo.OperationType.INSERT) {
            return TypeUtils.isVoid(returnType) || TypeUtils.isNumber(returnType);
        }
        return true;
    }

    private MethodMatchInfo.OperationType findOperationType(String methodName, String query, boolean readOnly) {
        query = query.trim().toLowerCase(Locale.ENGLISH);
        if (query.startsWith(SELECT)) {
            return MethodMatchInfo.OperationType.QUERY;
        } else if (query.startsWith(DELETE)) {
            return MethodMatchInfo.OperationType.DELETE;
        } else if (query.startsWith(UPDATE)) {
            if (DeleteMethod.METHOD_PATTERN.matcher(methodName.toLowerCase(Locale.ENGLISH)).matches()) {
                return MethodMatchInfo.OperationType.DELETE;
            }
            return MethodMatchInfo.OperationType.UPDATE;
        } else if (query.startsWith(INSERT)) {
            return MethodMatchInfo.OperationType.INSERT;
        }
        if (readOnly) {
            return MethodMatchInfo.OperationType.QUERY;
        }
        return MethodMatchInfo.OperationType.UPDATE;
    }

    /**
     * Builds a raw query for the given match context. Should be called for methods annotated with {@link Query} explicitly.
     */
    private RawQuery buildRawQuery(@NonNull MethodMatchContext matchContext,
                                   ParameterElement entityParameter,
                                   ParameterElement entitiesParameter,
                                   MethodMatchInfo.OperationType operationType) {
        MethodElement methodElement = matchContext.getMethodElement();
        String queryString = methodElement.stringValue(Query.class).orElseThrow(() ->
                new IllegalStateException("Should only be called if Query has value!")
        );
        List<ParameterElement> parameters = Arrays.asList(matchContext.getParameters());
        boolean namedParameters = matchContext.getRepositoryClass()
                .booleanValue(RepositoryConfiguration.class, "namedParameters").orElse(true);
        Matcher matcher = QueryBuilder.VARIABLE_PATTERN.matcher(queryString);

        SourcePersistentEntity persistentEntity = null;
        if (entityParameter != null) {
            persistentEntity = matchContext.getEntity(entityParameter.getGenericType());
        } else if (entitiesParameter != null) {
            persistentEntity = matchContext.getEntity(entitiesParameter.getGenericType().getFirstTypeArgument().get());
        }
        List<QueryParameterBinding> parameterBindings = new ArrayList<>(parameters.size());
        if (namedParameters) {
            while (matcher.find()) {
                String name = matcher.group(3);
                Optional<ParameterElement> element = parameters.stream()
                        .filter(p -> p.stringValue(Parameter.class).orElse(p.getName()).equals(name))
                        .findFirst();
                if (element.isPresent()) {
                    parameterBindings.add(QueryParameterBinding.of(name, name, DataType.OBJECT, new QueryParameter(name)));
                } else if (persistentEntity != null) {
                    PersistentPropertyPath propertyPath = persistentEntity.getPropertyPath(name);
                    if (propertyPath == null) {
                        matchContext.fail("Cannot update non-existent property: " + name);
                    } else {
                        parameterBindings.add(QueryParameterBinding.of(name, propertyPath.getPath(), propertyPath.getProperty().getDataType()));
                    }
                } else {
                    matchContext.fail("No method parameter found for named Query parameter: " + name);
                    return null;
                }
            }
        } else {
            int index = 1;
            while (matcher.find()) {
                String name = matcher.group(3);
                Optional<ParameterElement> element = parameters.stream()
                        .filter(p -> p.stringValue(Parameter.class).orElse(p.getName()).equals(name))
                        .findFirst();
                if (element.isPresent()) {
                    parameterBindings.add(QueryParameterBinding.of(String.valueOf(index++), name, DataType.OBJECT, new QueryParameter(name)));
                } else if (persistentEntity != null) {
                    PersistentPropertyPath propertyPath = persistentEntity.getPropertyPath(name);
                    if (propertyPath == null) {
                        matchContext.fail("Cannot update non-existent property: " + name);
                    } else {
                        parameterBindings.add(QueryParameterBinding.of(String.valueOf(index++), propertyPath.getPath(), propertyPath.getProperty().getDataType()));
                    }
                } else {
                    matchContext.fail("No method parameter found for named Query parameter: " + name);
                    return null;
                }
            }
        }
        boolean encodeEntityParameters = persistentEntity != null || operationType == MethodMatchInfo.OperationType.INSERT;
        return new RawQuery(matchContext.getRootEntity(), parameterBindings, encodeEntityParameters);
    }

}
