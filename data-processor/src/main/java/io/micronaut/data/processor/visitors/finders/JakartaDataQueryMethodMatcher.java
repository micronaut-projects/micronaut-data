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
package io.micronaut.data.processor.visitors.finders;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.First;
import io.micronaut.data.annotation.Projection;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCommonAbstractCriteria;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaDelete;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQuery;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaUpdate;
import io.micronaut.data.model.jpa.criteria.impl.AbstractPersistentEntityCriteriaDelete;
import io.micronaut.data.model.jpa.criteria.impl.AbstractPersistentEntityCriteriaUpdate;
import io.micronaut.data.model.jpa.criteria.impl.AbstractPersistentEntityQuery;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.processor.jq.JQCriteriaBuilderUtils;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaQuery;
import io.micronaut.data.processor.model.criteria.impl.MethodMatchSourcePersistentEntityCriteriaBuilderImpl;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.processing.ProcessingException;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The Jakarta Query annotation matcher.
 *
 * @author Denis Stepanov
 * @since 4.13
 */
@Internal
public final class JakartaDataQueryMethodMatcher implements MethodMatcher {

    @Override
    @Nullable
    public MethodMatch match(MethodMatchContext matchContext) {
        Optional<String> jakartaQuery = matchContext.getMethodElement().stringValue("jakarta.data.repository.Query");
        if (jakartaQuery.isPresent()) {

            Function<String, ClassElement> findClassElementFn = name -> {
                if (matchContext.hasRootEntity()) {
                    SourcePersistentEntity rootEntity = matchContext.getRootEntity();
                    if (rootEntity.getSimpleName().equals(name)) {
                        return rootEntity.getClassElement();
                    }
                }
                SourcePersistentEntity persistentEntity = matchContext.getEntityBySimplyNameResolver().apply(name);
                if (persistentEntity != null) {
                    return persistentEntity.getClassElement();
                }
                return matchContext.getVisitorContext().getClassElement(name)
                    .orElseThrow(() -> new ProcessingException(matchContext.getMethodElement(), "Unable to find an entity: " + name));
            };
            PersistentEntityCommonAbstractCriteria criteriaQuery = JQCriteriaBuilderUtils.build(
                jakartaQuery.get(),
                matchContext.hasRootEntity() ? matchContext.getRootEntity() : null,
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
                return getSelectQuery(criteriaQuery, jakartaQuery.get(), findClassElementFn);
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

                QueryResult queryResult = criteriaQuery.build(annotationMetadataHierarchy, queryBuilder);

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
                QueryResult queryResult = criteriaQuery.build(annotationMetadataHierarchy, queryBuilder);

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
                ClassElement interceptorType = interceptorMatch.interceptor();

                final AnnotationMetadataHierarchy annotationMetadataHierarchy = new AnnotationMetadataHierarchy(
                    matchContext.getRepositoryClass().getAnnotationMetadata(),
                    matchContext.getAnnotationMetadata()
                );

                QueryBuilder queryBuilder = matchContext.getQueryBuilder();

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

                SourcePersistentEntityCriteriaQuery<?> criteriaQueryInternal = (SourcePersistentEntityCriteriaQuery) criteriaQuery;

                applyProjections(matchContext, criteriaQueryInternal);
                applyFirst(matchContext, criteriaQueryInternal);

                MethodResult result = analyzeMethodResult(
                    matchContext,
                    criteriaQueryInternal.getQueryResultTypeName(),
                    matchContext.getRootEntity().getClassElement(),
                    interceptorMatch,
                    false
                );

                if (result.isDto() && criteriaQueryInternal.getSelection() == null) {
                    List<SourcePersistentProperty> dtoProjectionProperties = getDtoProjectionProperties(matchContext.getRootEntity(), matchContext.getMethodElement(), result.resultType());
                    if (!dtoProjectionProperties.isEmpty()) {
                        Root<?> root = criteriaQueryInternal.getRoots().iterator().next();
                        List<Selection<?>> selectionList = dtoProjectionProperties.stream()
                            .map(p -> {
                                matchContext.getMethodElement().annotate(Projection.class, b -> b.value(p.getName()));
                                if (matchContext.getQueryBuilder() instanceof SqlQueryBuilder) {
                                    return root.get(p.getName());
                                } else {
                                    return root.get(p.getName()).alias(p.getName());
                                }
                            })
                            .collect(Collectors.toList());
                        criteriaQueryInternal.multiselect(selectionList);
                    }
                }

                QueryResult queryResult = criteriaQuery.build(annotationMetadataHierarchy, queryBuilder);

                ClassElement genericReturnType = matchContext.getReturnType();
                if (matchContext.isTypeInRole(genericReturnType, TypeRole.PAGE)
                    || matchContext.isTypeInRole(genericReturnType, TypeRole.CURSORED_PAGE)) {

                    PersistentEntityCommonAbstractCriteria countCriteriaQuery = JQCriteriaBuilderUtils.buildCount(
                        query,
                        matchContext.getRootEntity(),
                        matchContext.getMethodElement(),
                        findClassElementFn,
                        new MethodMatchSourcePersistentEntityCriteriaBuilderImpl(matchContext)
                    );

                    QueryResult countQueryResult = countCriteriaQuery.build(annotationMetadataHierarchy, queryBuilder);
                    return new MethodMatchInfo(
                        getOperationType(),
                        result.resultType(),
                        interceptorType
                    )   .dto(result.isDto())
                        .queryResult(queryResult)
                        .countQueryResult(countQueryResult);
                }

                return new MethodMatchInfo(
                    getOperationType(),
                    result.resultType(),
                    interceptorType
                )   .dto(result.isDto())
                    .queryResult(queryResult);
            }
        };
    }

    /**
     * Applies the projections declared by {@link Projection} (mapped from Jakarta Data {@code @Select})
     * to a JDQL query that doesn't already select something explicitly.
     *
     * @param matchContext The match context
     * @param criteriaQuery The criteria query
     */
    private static void applyProjections(MethodMatchContext matchContext,
                                         SourcePersistentEntityCriteriaQuery<?> criteriaQuery) {
        if (criteriaQuery.getSelection() != null) {
            return;
        }
        List<String> projections = matchContext.getMethodElement().getAnnotationValuesByType(Projection.class)
            .stream()
            .flatMap(av -> av.stringValue().stream())
            .filter(value -> !value.isBlank())
            .toList();
        if (projections.isEmpty()) {
            return;
        }
        Root<?> root = criteriaQuery.getRoots().iterator().next();
        List<Selection<?>> selections = projections.stream()
            .<Selection<?>>map(projection -> {
                // A projection may name a path into an embedded or associated entity
                Path<?> path = root;
                for (String segment : StringUtils.splitOmitEmptyStrings(projection, '.')) {
                    path = path.get(segment);
                }
                return (Selection<?>) path;
            })
            .toList();
        if (selections.size() == 1) {
            criteriaQuery.select((Selection) selections.getFirst());
        } else {
            criteriaQuery.multiselect(selections);
        }
    }

    /**
     * Applies the limit declared by {@link First} (mapped from Jakarta Data {@code @First}) to a JDQL query.
     * JDQL itself has no limit clause, so the annotation is the only static source of one.
     *
     * @param matchContext The match context
     * @param criteriaQuery The criteria query
     */
    private static void applyFirst(MethodMatchContext matchContext,
                                   SourcePersistentEntityCriteriaQuery<?> criteriaQuery) {
        AnnotationValue<First> firstAnnotation = matchContext.getMethodElement().getAnnotation(First.class);
        if (firstAnnotation != null) {
            criteriaQuery.limit(firstAnnotation.intValue().orElse(1));
        }
    }

    @Override
    public int getOrder() {
        return MethodMatcher.DEFAULT_POSITION - 3000;
    }
}
