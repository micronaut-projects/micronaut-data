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
package io.micronaut.data.document.processor.matchers;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Introspected;
import org.jspecify.annotations.NonNull;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.ParameterExpression;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.document.mongo.MongoAnnotations;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.query.BindingParameter;
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
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Finder with custom defied query used to return a single result.
 *
 * @author Denis Stepanov
 * @since 3.3.0
 */
public class MongoRawQueryMethodMatcher implements MethodMatcher {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("([^:]*)((?<![:]):([a-zA-Z]+[a-zA-Z0-9]*))([^:]*)");

    @Override
    public final int getOrder() {
        // should run first and before `RawQueryMethodMatcher`
        return DEFAULT_POSITION - 2000;
    }

    @Override
    @Nullable
    public MethodMatch match(MethodMatchContext matchContext) {
        AnnotationMetadata annotationMetadata = matchContext.getAnnotationMetadata();
        if (!annotationMetadata.hasAnnotation(MongoAnnotations.REPOSITORY)) {
            return null;
        }
        if (annotationMetadata.hasAnnotation(MongoAnnotations.FIND_QUERY)
                || annotationMetadata.hasAnnotation(MongoAnnotations.AGGREGATION_QUERY)) {
            return methodMatchByFilterQuery(DataMethod.OperationType.QUERY);
        }
        if (annotationMetadata.hasAnnotation(MongoAnnotations.DELETE_QUERY)) {
            return methodMatchByFilterQuery(DataMethod.OperationType.DELETE);
        }
        if (annotationMetadata.hasAnnotation(MongoAnnotations.UPDATE_QUERY)
            && annotationMetadata.hasAnnotation(MongoAnnotations.UPDATE_RETURNING_QUERY)) {
            throw new MatchFailedException("`@MongoUpdateQuery` and `@MongoUpdateReturningQuery` are mutually exclusive. Use only one on a method.");
        }
        if (annotationMetadata.hasAnnotation(MongoAnnotations.UPDATE_QUERY)) {
            return methodMatchByFilterQuery(DataMethod.OperationType.UPDATE);
        }
        if (annotationMetadata.hasAnnotation(MongoAnnotations.UPDATE_RETURNING_QUERY)) {
            return methodMatchByUpdateReturningQuery();
        }
        if (annotationMetadata.stringValue(Query.class).isPresent()) {
            throw new MatchFailedException("`@Query` annotations is not supported for MongoDB repositories. Use one of the annotations from `io.micronaut.data.mongodb.annotation` for a custom query.");
        }
        return null;
    }

    private void removeAnnotation(AnnotationMetadata annotationMetadata, String annotation) {
        if (annotationMetadata instanceof AnnotationMetadataHierarchy hierarchy) {
            removeAnnotation(hierarchy.getDeclaredMetadata(), annotation);
            removeAnnotation(hierarchy.getRootMetadata(), annotation);
            return;
        }
        if (annotationMetadata instanceof MutableAnnotationMetadata mutableAnnotationMetadata) {
            mutableAnnotationMetadata.removeAnnotation(annotation);
            mutableAnnotationMetadata.removeStereotype(annotation);
        }
    }

    private MethodMatch methodMatchByUpdateReturningQuery() {
        return new MethodMatch() {

            @Override
            public MethodMatchInfo buildMatchInfo(MethodMatchContext matchContext) {
                MethodElement methodElement = matchContext.getMethodElement();
                ClassElement returnType = matchContext.getReturnType();
                ClassElement producedType = TypeUtils.getMethodProducingItemType(methodElement);
                if (producedType == null || TypeUtils.isVoid(producedType)) {
                    throw new MatchFailedException("MongoDB @MongoUpdateReturningQuery requires a non-void single return type");
                }
                if (isMultipleResultType(producedType)) {
                    throw new MatchFailedException(updateReturningSingleResultMessage(returnType));
                }
                if (TypeUtils.isReactiveType(returnType)) {
                    if (!TypeUtils.isReactiveSingleResult(returnType)) {
                        throw new MatchFailedException("MongoDB update returning supports only a single result. Use a single-item reactive type (e.g. Mono<T>).");
                    }
                }
                MethodMatchInfo matchInfo = methodMatchByFilterQuery(DataMethod.OperationType.UPDATE_RETURNING).buildMatchInfo(matchContext);
                if (matchInfo == null) {
                    throw new MatchFailedException("MongoDB update returning match info could not be created");
                }
                return matchInfo;
            }
        };
    }

    private MethodMatch methodMatchByFilterQuery(DataMethod.OperationType operationType) {
        return new MethodMatch() {

            @Override
            public MethodMatchInfo buildMatchInfo(MethodMatchContext matchContext) {
                if (operationType == DataMethod.OperationType.UPDATE_RETURNING) {
                    MethodElement methodElement = matchContext.getMethodElement();
                    ClassElement producedType = TypeUtils.getMethodProducingItemType(methodElement);
                    if (producedType == null || TypeUtils.isVoid(producedType)) {
                        throw new MatchFailedException("MongoDB @MongoUpdateReturningQuery requires a non-void single return type");
                    }
                    if (isMultipleResultType(producedType)) {
                        throw new MatchFailedException(updateReturningSingleResultMessage(matchContext.getReturnType()));
                    }
                }
                ParameterElement[] parameters = matchContext.getParameters();
                ParameterElement entityParameter;
                ParameterElement entitiesParameter;
                ParameterElement updateReturningOptionsParameter = null;
                if (parameters.length > 1) {
                    entityParameter = null;
                    entitiesParameter = null;
                } else {
                    entityParameter = Arrays.stream(parameters).filter(p -> TypeUtils.isEntity(p.getGenericType())).findFirst().orElse(null);
                    entitiesParameter = Arrays.stream(parameters).filter(p -> TypeUtils.isIterableOfEntity(p.getGenericType())).findFirst().orElse(null);
                }
                if (operationType == DataMethod.OperationType.UPDATE_RETURNING) {
                    updateReturningOptionsParameter = Arrays.stream(parameters)
                        .filter(p -> p.getType().isAssignable(MongoAnnotations.UPDATE_RETURNING_OPTIONS_BEAN))
                        .findFirst()
                        .orElse(null);
                }

                FindersUtils.InterceptorMatch entry = FindersUtils.resolveInterceptorTypeByOperationType(
                        entityParameter != null,
                        entitiesParameter != null,
                        operationType,
                        matchContext);

                ClassElement resultType = entry.returnType();
                ClassElement interceptorType = entry.interceptor();

                if (resultType == null) {
                    resultType = matchContext.getRootEntity().getType();
                }
                boolean isDto = false;
                if (resultType.hasAnnotation(Introspected.class) && !resultType.hasAnnotation(MappedEntity.class)) {
                    isDto = true;
                }

                MethodMatchInfo methodMatchInfo = new MethodMatchInfo(
                        operationType,
                        resultType,
                        interceptorType
                );

                methodMatchInfo.dto(isDto);

                buildRawQuery(matchContext, methodMatchInfo, entityParameter, entitiesParameter, operationType);

                if (entityParameter != null) {
                    methodMatchInfo.addParameterRole(entityParameter, TypeRole.ENTITY);
                } else if (entitiesParameter != null) {
                    methodMatchInfo.addParameterRole(entitiesParameter, TypeRole.ENTITIES);
                }
                if (updateReturningOptionsParameter != null) {
                    methodMatchInfo.addParameterRole(updateReturningOptionsParameter, MongoAnnotations.UPDATE_OPTIONS_ROLE);
                }
                return methodMatchInfo;
            }
        };
    }

    private static boolean isMultipleResultType(@Nullable ClassElement type) {
        return type != null && (type.isArray() || type.isAssignable(Iterable.class));
    }

    @NonNull
    private static String updateReturningSingleResultMessage(@NonNull ClassElement returnType) {
        if (TypeUtils.isFutureType(returnType)) {
            return "MongoDB update returning supports only a single result. Use CompletionStage<T>.";
        }
        if (TypeUtils.isReactiveType(returnType) || returnType.getName().equals("kotlinx.coroutines.flow.Flow")) {
            return "MongoDB update returning supports only a single result. Use a single-item reactive type (e.g. Mono<T>).";
        }
        return "MongoDB update returning supports only a single result";
    }

    private void buildRawQuery(@NonNull MethodMatchContext matchContext,
                               MethodMatchInfo methodMatchInfo,
                               @Nullable
                               ParameterElement entityParameter,
                               @Nullable
                               ParameterElement entitiesParameter,
                               DataMethod.OperationType operationType) {
        MethodElement methodElement = matchContext.getMethodElement();
        List<ParameterElement> parameters = Arrays.asList(matchContext.getParameters());
        ParameterElement entityParam = null;
        SourcePersistentEntity persistentEntity = null;
        if (entityParameter != null) {
            entityParam = entityParameter;
            persistentEntity = matchContext.getEntity(entityParameter.getGenericType());
        } else if (entitiesParameter != null) {
            entityParam = entitiesParameter;
            persistentEntity = matchContext.getEntity(entitiesParameter.getGenericType().getFirstTypeArgument()
                    .orElseThrow(IllegalStateException::new));
        }

        QueryResult queryResult;
        if (operationType == DataMethod.OperationType.UPDATE
            || methodElement.hasAnnotation(MongoAnnotations.UPDATE_QUERY)
            || methodElement.hasAnnotation(MongoAnnotations.UPDATE_RETURNING_QUERY)) {
            queryResult = getUpdateQueryResult(matchContext, parameters, entityParam, persistentEntity);
        } else {
            queryResult = getQueryResult(matchContext, parameters, entityParam, persistentEntity);
        }
        boolean encodeEntityParameters = persistentEntity != null || operationType == DataMethod.OperationType.INSERT;

        methodElement.annotate(Query.class, builder -> {
            if (queryResult.getUpdate() != null) {
                builder.member("update", queryResult.getUpdate());
            }
            builder.value(queryResult.getQuery());
        });

        methodMatchInfo
                .encodeEntityParameters(encodeEntityParameters)
                .queryResult(queryResult)
                .countQueryResult(null);
    }

    private QueryResult getQueryResult(MethodMatchContext matchContext,
                                       List<ParameterElement> parameters,
                                       @Nullable
                                       ParameterElement entityParam,
                                       @Nullable
                                       SourcePersistentEntity persistentEntity) {
        String filterQueryString;
        if (matchContext.getMethodElement().hasAnnotation(MongoAnnotations.AGGREGATION_QUERY)) {
            filterQueryString = matchContext.getMethodElement().stringValue(MongoAnnotations.AGGREGATION_QUERY).orElseThrow(() ->
                    new MatchFailedException("The pipeline value is missing!")
            );
            removeAnnotation(matchContext.getAnnotationMetadata(), MongoAnnotations.AGGREGATION_QUERY); // Mapped to query
        } else if (matchContext.getMethodElement().hasAnnotation(MongoAnnotations.FIND_QUERY)) {
            filterQueryString = matchContext.getMethodElement().stringValue(MongoAnnotations.FILTER).orElseThrow(() ->
                    new MatchFailedException("The filter value is missing!")
            );
            removeAnnotation(matchContext.getAnnotationMetadata(), MongoAnnotations.FILTER); // Mapped to query
            removeAnnotation(matchContext.getAnnotationMetadata(), MongoAnnotations.FIND_QUERY); // Mapped to query
        } else if (matchContext.getMethodElement().hasAnnotation(MongoAnnotations.DELETE_QUERY)) {
            filterQueryString = matchContext.getMethodElement().stringValue(MongoAnnotations.FILTER).orElseThrow(() ->
                    new MatchFailedException("The filter value is missing!")
            );
            removeAnnotation(matchContext.getAnnotationMetadata(), MongoAnnotations.FILTER); // Mapped to query
            removeAnnotation(matchContext.getAnnotationMetadata(), MongoAnnotations.DELETE_QUERY); // Mapped to query
        } else {
            throw new MatchFailedException("Unknown custom query annotation!");
        }
        List<QueryParameterBinding> parameterBindings = new ArrayList<>(parameters.size());
        String filterQuery = processCustomQuery(matchContext, filterQueryString, parameters, entityParam, persistentEntity, parameterBindings);
        return QueryResult.of(filterQuery, parameterBindings);
    }

    private QueryResult getUpdateQueryResult(MethodMatchContext matchContext,
                                             List<ParameterElement> parameters,
                                             @Nullable ParameterElement entityParam,
                                             @Nullable SourcePersistentEntity persistentEntity) {
        String filterQueryString = matchContext.getMethodElement().stringValue(MongoAnnotations.FILTER).orElse("{}");
        String updateAnnotation = matchContext.getMethodElement().hasAnnotation(MongoAnnotations.UPDATE_RETURNING_QUERY)
            ? MongoAnnotations.UPDATE_RETURNING_QUERY
            : MongoAnnotations.UPDATE_QUERY;
        String updateQueryString = matchContext.getMethodElement().stringValue(updateAnnotation, "update").orElseThrow(() ->
                new MatchFailedException("Update query is missing!")
        );
        removeAnnotation(matchContext.getAnnotationMetadata(), MongoAnnotations.FILTER); // Mapped to query
        removeAnnotation(matchContext.getAnnotationMetadata(), MongoAnnotations.UPDATE_QUERY); // Mapped to query
        List<QueryParameterBinding> parameterBindings = new ArrayList<>(parameters.size());
        String filterQuery = processCustomQuery(matchContext, filterQueryString, parameters, entityParam, persistentEntity, parameterBindings);
        String updateQuery = processCustomQuery(matchContext, updateQueryString, parameters, entityParam, persistentEntity, parameterBindings);
        return new QueryResult() {
            @Override
            public String getQuery() {
                return filterQuery;
            }

            @Override
            public String getUpdate() {
                return updateQuery;
            }

            @Override
            public List<String> getQueryParts() {
                return Collections.emptyList();
            }

            @Override
            public List<QueryParameterBinding> getParameterBindings() {
                return parameterBindings;
            }

        };
    }

    private String processCustomQuery(MethodMatchContext matchContext,
                                      String queryString,
                                      List<ParameterElement> parameters,
                                      @Nullable
                                      ParameterElement entityParam,
                                      @Nullable
                                      SourcePersistentEntity persistentEntity,
                                      List<QueryParameterBinding> parameterBindings) {
        List<AnnotationValue<ParameterExpression>> parameterExpressions = matchContext.getMethodElement()
            .getAnnotationMetadata()
            .getAnnotationValuesByType(ParameterExpression.class);

        java.util.regex.Matcher matcher = VARIABLE_PATTERN.matcher(queryString);
        List<String> queryParts = new ArrayList<>();
        int lastOffset = 0;
        while (matcher.find()) {
            int matcherStart = matcher.start(3);
            String start = queryString.substring(lastOffset, matcherStart - 1);
            lastOffset = matcher.end(3);
            if (!start.isEmpty()) {
                queryParts.add(start);
            }

            String name = matcher.group(3);

            QueryParameterBinding binding = RawQueryMethodMatcher.addBinding(
                matchContext,
                parameters,
                parameterExpressions,
                entityParam,
                persistentEntity,
                name,
                BindingParameter.BindingContext.create().name(name)
            );

            parameterBindings.add(binding);

            int ind = parameterBindings.size() - 1;
            queryParts.add("{$mn_qp:" + ind + "}");
        }
        String end = queryString.substring(lastOffset);
        if (!end.isEmpty()) {
            queryParts.add(end);
        }
        return String.join("", queryParts);
    }

}
