/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.data.processor.visitors.finders.annotated;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCommonAbstractCriteria;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaDelete;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQuery;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaUpdate;
import io.micronaut.data.model.jpa.criteria.impl.AbstractPersistentEntityCriteriaDelete;
import io.micronaut.data.model.jpa.criteria.impl.AbstractPersistentEntityCriteriaUpdate;
import io.micronaut.data.model.jpa.criteria.impl.AbstractPersistentEntityQuery;
import io.micronaut.data.model.jpa.criteria.impl.QueryResultPersistentEntityCriteriaQuery;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.processor.jdql.JDQLCriteriaBuilderUtils;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.criteria.impl.MethodMatchSourcePersistentEntityCriteriaBuilderImpl;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.data.processor.visitors.finders.AbstractCriteriaMethodMatch;
import io.micronaut.data.processor.visitors.finders.FindersUtils;
import io.micronaut.data.processor.visitors.finders.MethodMatchInfo;
import io.micronaut.data.processor.visitors.finders.MethodMatcher;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.processing.ProcessingException;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * The Jakarta Data Query annotation matcher.
 *
 * @author Denis Stepanov
 * @since 4.13
 */
@Internal
public final class JakartaDataQueryAnnotatedMethodMatcher implements MethodMatcher {

    @Override
    public MethodMatch match(MethodMatchContext matchContext) {
        Optional<String> jdqlQuery = matchContext.getMethodElement().stringValue("jakarta.data.repository.Query");
        if (jdqlQuery.isPresent()) {

            Function<String, ClassElement> findClassElementFn = name -> {
                SourcePersistentEntity rootEntity = matchContext.getRootEntity();
                if (rootEntity.getSimpleName().equals(name)) {
                    return rootEntity.getClassElement();
                }
                SourcePersistentEntity persistentEntity = matchContext.getEntityBySimplyNameResolver().apply(name);
                if (persistentEntity != null) {
                    return persistentEntity.getClassElement();
                }
                return matchContext.getVisitorContext().getClassElement(name)
                    .orElseThrow(() -> new ProcessingException(matchContext.getMethodElement(), "Unable to find an entity: " + name));
            };
            PersistentEntityCommonAbstractCriteria criteriaQuery = JDQLCriteriaBuilderUtils.build(
                jdqlQuery.get(),
                matchContext.getRootEntity(),
                matchContext.getMethodElement(),
                findClassElementFn,
                new MethodMatchSourcePersistentEntityCriteriaBuilderImpl(matchContext)
            );
            matchContext.setRootEntity((SourcePersistentEntity) criteriaQuery.getPersistentEntity());

            if (criteriaQuery instanceof PersistentEntityCriteriaUpdate<?>) {
                return getUpdateQuery(criteriaQuery);
            }
            if (criteriaQuery instanceof PersistentEntityCriteriaDelete<?>) {
                return getDeleteQuery(criteriaQuery);
            }
            if (criteriaQuery instanceof PersistentEntityCriteriaQuery<?>) {
                return getSelectQuery(criteriaQuery, jdqlQuery.get(), findClassElementFn);
            }
            return null;
        }
        return null;
    }

    private static AbstractCriteriaMethodMatch getUpdateQuery(PersistentEntityCommonAbstractCriteria criteriaQuery) {
        return new AbstractCriteriaMethodMatch(List.of()) {
            @Override
            protected DataMethod.OperationType getOperationType() {
                return DataMethod.OperationType.UPDATE;
            }

            @Override
            protected MethodMatchInfo build(MethodMatchContext matchContext) {
                FindersUtils.InterceptorMatch interceptorMatch = resolveReturnTypeAndInterceptor(matchContext);
                ClassElement resultType = interceptorMatch.returnType();
                boolean isDto = false;
                ClassElement interceptorType = interceptorMatch.interceptor();

                AbstractPersistentEntityCriteriaUpdate<?> query = (AbstractPersistentEntityCriteriaUpdate<?>) criteriaQuery;

                boolean optimisticLock = query.hasVersionRestriction();

                final AnnotationMetadataHierarchy annotationMetadataHierarchy = new AnnotationMetadataHierarchy(
                    matchContext.getRepositoryClass().getAnnotationMetadata(),
                    matchContext.getAnnotationMetadata()
                );
                QueryBuilder queryBuilder = matchContext.getQueryBuilder();

                QueryResult queryResult = ((QueryResultPersistentEntityCriteriaQuery) criteriaQuery).buildQuery(annotationMetadataHierarchy, queryBuilder);

                return new MethodMatchInfo(
                    getOperationType(),
                    resultType,
                    interceptorType
                )
                    .dto(isDto)
                    .optimisticLock(optimisticLock)
                    .queryResult(queryResult);
            }
        };
    }

    private static AbstractCriteriaMethodMatch getDeleteQuery(PersistentEntityCommonAbstractCriteria criteriaQuery) {
        return new AbstractCriteriaMethodMatch(List.of()) {

            @Override
            protected DataMethod.OperationType getOperationType() {
                return DataMethod.OperationType.DELETE;
            }

            @Override
            protected MethodMatchInfo build(MethodMatchContext matchContext) {
                FindersUtils.InterceptorMatch interceptorMatch = resolveReturnTypeAndInterceptor(matchContext);
                ClassElement resultType = interceptorMatch.returnType();
                ClassElement interceptorType = interceptorMatch.interceptor();

                boolean optimisticLock = ((AbstractPersistentEntityCriteriaDelete<?>) criteriaQuery).hasVersionRestriction();

                final AnnotationMetadataHierarchy annotationMetadataHierarchy = new AnnotationMetadataHierarchy(
                    matchContext.getRepositoryClass().getAnnotationMetadata(),
                    matchContext.getAnnotationMetadata()
                );

                QueryBuilder queryBuilder = matchContext.getQueryBuilder();
                QueryResult queryResult = ((QueryResultPersistentEntityCriteriaQuery) criteriaQuery).buildQuery(annotationMetadataHierarchy, queryBuilder);

                return new MethodMatchInfo(
                    getOperationType(),
                    resultType,
                    interceptorType
                )
                    .optimisticLock(optimisticLock)
                    .queryResult(queryResult);
            }
        };
    }

    private static AbstractCriteriaMethodMatch getSelectQuery(PersistentEntityCommonAbstractCriteria criteriaQuery,
                                                              String query,
                                                              Function<String, ClassElement> findClassElementFn) {
        return new AbstractCriteriaMethodMatch(List.of()) {

            @Override
            protected DataMethod.OperationType getOperationType() {
                return DataMethod.OperationType.QUERY;
            }

            @Override
            protected MethodMatchInfo build(MethodMatchContext matchContext) {
                FindersUtils.InterceptorMatch interceptorMatch = resolveReturnTypeAndInterceptor(matchContext);
                ClassElement resultType = interceptorMatch.returnType();
                ClassElement interceptorType = interceptorMatch.interceptor();

                final AnnotationMetadataHierarchy annotationMetadataHierarchy = new AnnotationMetadataHierarchy(
                    matchContext.getRepositoryClass().getAnnotationMetadata(),
                    matchContext.getAnnotationMetadata()
                );

                QueryBuilder queryBuilder = matchContext.getQueryBuilder();
                QueryResultPersistentEntityCriteriaQuery persistentEntityCriteriaQuery = (QueryResultPersistentEntityCriteriaQuery) criteriaQuery;

                if (matchContext.hasParameterInRole(TypeRole.PAGEABLE)) {
                    Element pageableParameter = matchContext.findParameterInRole(TypeRole.PAGEABLE);
                    AbstractPersistentEntityQuery<?, ?> abstractPersistentEntityQuery = (AbstractPersistentEntityQuery<?, ?>) criteriaQuery;
                    abstractPersistentEntityQuery.getParametersInRole().put(List.of(matchContext.getParameters()).indexOf(pageableParameter), TypeRole.PAGEABLE);
                } else if (matchContext.hasParameterInRole(TypeRole.SORT)) {
                    Element sortParameter = matchContext.findParameterInRole(TypeRole.SORT);
                    AbstractPersistentEntityQuery<?, ?> abstractPersistentEntityQuery = (AbstractPersistentEntityQuery<?, ?>) criteriaQuery;
                    abstractPersistentEntityQuery.getParametersInRole().put(List.of(matchContext.getParameters()).indexOf(sortParameter), TypeRole.SORT);
                } else if (matchContext.hasParameterInRole(TypeRole.LIMIT)) {
                    Element limitParameter = matchContext.findParameterInRole(TypeRole.LIMIT);
                    AbstractPersistentEntityQuery<?, ?> abstractPersistentEntityQuery = (AbstractPersistentEntityQuery<?, ?>) criteriaQuery;
                    abstractPersistentEntityQuery.getParametersInRole().put(List.of(matchContext.getParameters()).indexOf(limitParameter), TypeRole.LIMIT);
                }

                QueryResult queryResult = persistentEntityCriteriaQuery.buildQuery(annotationMetadataHierarchy, queryBuilder);

                ClassElement genericReturnType = matchContext.getReturnType();
                if (matchContext.isTypeInRole(genericReturnType, TypeRole.PAGE)
                    || matchContext.isTypeInRole(genericReturnType, TypeRole.CURSORED_PAGE)) {

                    PersistentEntityCommonAbstractCriteria countCriteriaQuery = JDQLCriteriaBuilderUtils.buildCount(
                        query,
                        matchContext.getRootEntity(),
                        matchContext.getMethodElement(),
                        findClassElementFn,
                        new MethodMatchSourcePersistentEntityCriteriaBuilderImpl(matchContext)
                    );

                    QueryResult countQueryResult = ((QueryResultPersistentEntityCriteriaQuery) countCriteriaQuery).buildQuery(annotationMetadataHierarchy, queryBuilder);
                    return new MethodMatchInfo(
                        getOperationType(),
                        resultType,
                        interceptorType
                    )
                        .queryResult(queryResult)
                        .countQueryResult(countQueryResult);
                }

                return new MethodMatchInfo(
                    getOperationType(),
                    resultType,
                    interceptorType
                )
                    .queryResult(queryResult);
            }
        };
    }

    @Override
    public int getOrder() {
        return MethodMatcher.DEFAULT_POSITION - 3000;
    }
}
