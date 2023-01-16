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

import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.document.mongo.MongoAnnotations;
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
import io.micronaut.data.processor.visitors.finders.FindersUtils;
import io.micronaut.data.processor.visitors.finders.MethodMatchInfo;
import io.micronaut.data.processor.visitors.finders.MethodMatcher;
import io.micronaut.data.processor.visitors.finders.TypeUtils;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Finder with custom defied query used to return a single result.
 *
 * @author Denis Stepanov
 * @since 3.3.0
 */
public class MongoRawQueryMethodMatcher implements MethodMatcher {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("([^:]*)((?<![:]):([a-zA-Z]+[a-zA-Z0-9]*))([^:]*)");

    /**
     * Default constructor.
     */
    public MongoRawQueryMethodMatcher() {
    }

    @Override
    public final int getOrder() {
        // should run first and before `RawQueryMethodMatcher`
        return DEFAULT_POSITION - 2000;
    }

    @Override
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
        if (annotationMetadata.hasAnnotation(MongoAnnotations.UPDATE_QUERY)) {
            return methodMatchByFilterQuery(DataMethod.OperationType.UPDATE);
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
        if (annotationMetadata instanceof MutableAnnotationMetadata) {
            ((MutableAnnotationMetadata) annotationMetadata).removeAnnotation(annotation);
            ((MutableAnnotationMetadata) annotationMetadata).removeStereotype(annotation);
        }
    }

    private MethodMatch methodMatchByFilterQuery(DataMethod.OperationType operationType) {
        return new MethodMatch() {

            @Override
            public MethodMatchInfo buildMatchInfo(MethodMatchContext matchContext) {
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

                Map.Entry<ClassElement, Class<? extends DataInterceptor>> entry = FindersUtils.resolveInterceptorTypeByOperationType(
                        entityParameter != null,
                        entitiesParameter != null,
                        operationType,
                        matchContext);

                ClassElement resultType = entry.getKey();
                Class<? extends DataInterceptor> interceptorType = entry.getValue();

                boolean isDto = false;
                if (resultType == null) {
                    resultType = matchContext.getRootEntity().getType();
                } else {
                    if (resultType.hasAnnotation(Introspected.class)) {
                        if (!resultType.hasAnnotation(MappedEntity.class)) {
                            isDto = true;
                        }
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

    private void buildRawQuery(@NonNull MethodMatchContext matchContext,
                               MethodMatchInfo methodMatchInfo,
                               ParameterElement entityParameter,
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
        if (operationType == DataMethod.OperationType.UPDATE) {
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
                                       ParameterElement entityParam,
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
        return new QueryResult() {
            @Override
            public String getQuery() {
                return filterQuery;
            }

            @Override
            public List<String> getQueryParts() {
                return Collections.emptyList();
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

    private QueryResult getUpdateQueryResult(MethodMatchContext matchContext,
                                       List<ParameterElement> parameters,
                                       ParameterElement entityParam,
                                       SourcePersistentEntity persistentEntity) {
        String filterQueryString = matchContext.getMethodElement().stringValue(MongoAnnotations.FILTER).orElseThrow(() ->
                new MatchFailedException("Filter query is missing!")
        );
        String updateQueryString = matchContext.getMethodElement().stringValue(MongoAnnotations.UPDATE_QUERY, "update").orElseThrow(() ->
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

            @Override
            public Map<String, String> getAdditionalRequiredParameters() {
                return Collections.emptyMap();
            }
        };
    }

    private String processCustomQuery(MethodMatchContext matchContext, String queryString, List<ParameterElement> parameters, ParameterElement entityParam, SourcePersistentEntity persistentEntity, List<QueryParameterBinding> parameterBindings) {
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

            int ind = parameterBindings.size() - 1;
            queryParts.add("{$mn_qp:" + ind + "}");
        }
        String end = queryString.substring(lastOffset);
        if (!end.isEmpty()) {
            queryParts.add(end);
        }
        return String.join("", queryParts);
    }

    private SourceParameterExpressionImpl bindingParameter(MethodMatchContext matchContext, ParameterElement element) {
        return bindingParameter(matchContext, element, false);
    }

    private SourceParameterExpressionImpl bindingParameter(MethodMatchContext matchContext, ParameterElement element, boolean isEntityParameter) {
        return new SourceParameterExpressionImpl(
                Collections.emptyMap(),
                matchContext.getParameters(),
                element,
                isEntityParameter);
    }

}
