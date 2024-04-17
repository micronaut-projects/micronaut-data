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
package io.micronaut.data.processor.visitors.finders.criteria;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaBuilder;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaUpdate;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.impl.AbstractPersistentEntityCriteriaUpdate;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaBuilder;
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaUpdate;
import io.micronaut.data.processor.model.criteria.impl.MethodMatchSourcePersistentEntityCriteriaBuilderImpl;
import io.micronaut.data.processor.visitors.MatchFailedException;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.data.processor.visitors.finders.AbstractCriteriaMethodMatch;
import io.micronaut.data.processor.visitors.finders.FindersUtils;
import io.micronaut.data.processor.visitors.finders.MethodMatchInfo;
import io.micronaut.data.processor.visitors.finders.MethodNameParser;
import io.micronaut.data.processor.visitors.finders.QueryMatchId;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ParameterElement;
import jakarta.persistence.criteria.Selection;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Update criteria method match.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Experimental
public class UpdateCriteriaMethodMatch extends AbstractCriteriaMethodMatch {

    private final boolean isReturning;

    /**
     * Default constructor.
     *
     * @param matches     The matches
     * @param isReturning The returning
     */
    public UpdateCriteriaMethodMatch(List<MethodNameParser.Match> matches, boolean isReturning) {
        super(matches);
        this.isReturning = isReturning;
    }

    /**
     * Apply query match.
     *
     * @param matchContext The match context
     * @param root         The root
     * @param query        The query
     * @param cb           The criteria builder
     * @param <T>          The entity type
     */
    protected <T> void apply(MethodMatchContext matchContext,
                             PersistentEntityRoot<T> root,
                             PersistentEntityCriteriaUpdate<T> query,
                             SourcePersistentEntityCriteriaBuilder cb) {

        boolean predicatedApplied = false;
        boolean projectionApplied = false;
        for (MethodNameParser.Match match : matches) {
            if (match.id() == QueryMatchId.PREDICATE) {
                applyPredicates(match.part(), matchContext.getParameters(), root, query, cb);
                predicatedApplied = true;
            }
            if (match.id() == QueryMatchId.RETURNING) {
                applyProjections(match.part(), root, query, cb);
                projectionApplied = true;
            }
        }
        if (!predicatedApplied) {
            applyPredicates(root, query, cb);
        }
        if (!projectionApplied) {
            applyProjections("", root, query, cb);
        }

        SourcePersistentEntity entity = matchContext.getRootEntity();

        addPropertiesToUpdate(matchContext, root, query, cb);

        AbstractPersistentEntityCriteriaUpdate<T> criteriaUpdate = (AbstractPersistentEntityCriteriaUpdate<T>) query;

        // Add updatable auto-populated parameters
        entity.getPersistentProperties().stream()
                .filter(p -> p != null && p.findAnnotation(AutoPopulated.class).map(ap -> ap.getRequiredValue(AutoPopulated.UPDATEABLE, Boolean.class)).orElse(false))
                .forEach(p -> query.set(p.getName(), cb.parameter((ParameterElement) null)));

        if (entity.getVersion() != null && !entity.getVersion().isGenerated() && criteriaUpdate.hasVersionRestriction()) {
            query.set(entity.getVersion().getName(), cb.parameter((ParameterElement) null));
        }

        if (criteriaUpdate.getUpdateValues().isEmpty()) {
            throw new MatchFailedException("At least one parameter required to update");
        }
    }

    /**
     * Apply projections.
     *
     * @param projection The querySequence
     * @param root          The root
     * @param query         The query
     * @param cb            The criteria builder
     * @param <T>           The entity type
     */
    protected <T> void applyProjections(String projection,
                                        PersistentEntityRoot<T> root,
                                        PersistentEntityCriteriaUpdate<T> query,
                                        PersistentEntityCriteriaBuilder cb) {
        if (!isReturning) {
            return;
        }
        List<Selection<?>> selections = findSelections(projection, root, cb, null);
        if (selections.isEmpty()) {
            query.returning(root);
        } else if (selections.size() == 1) {
            query.returning((Selection<? extends T>) selections.get(0));
        } else {
            throw new MatchFailedException("Multi-selection is not supported");
        }
    }

    protected <T> void addPropertiesToUpdate(MethodMatchContext matchContext,
                                             PersistentEntityRoot<T> root,
                                             PersistentEntityCriteriaUpdate<T> query,
                                             SourcePersistentEntityCriteriaBuilder cb) {
    }

    @Override
    protected MethodMatchInfo build(MethodMatchContext matchContext) {

        MethodMatchSourcePersistentEntityCriteriaBuilderImpl cb = new MethodMatchSourcePersistentEntityCriteriaBuilderImpl(matchContext);

        PersistentEntityCriteriaUpdate<Object> criteriaQuery = cb.createCriteriaUpdate(null);
        PersistentEntityRoot<Object> root = criteriaQuery.from(matchContext.getRootEntity());

        apply(matchContext, root, criteriaQuery, cb);

        FindersUtils.InterceptorMatch interceptorMatch = resolveReturnTypeAndInterceptor(matchContext);
        ClassElement resultType = interceptorMatch.returnType();
        ClassElement interceptorType = interceptorMatch.interceptor();

        SourcePersistentEntityCriteriaUpdate<?> criteriaUpdate = (SourcePersistentEntityCriteriaUpdate<?>) criteriaQuery;

        MethodResult result = analyzeMethodResult(
            matchContext,
            criteriaUpdate.getQueryResultTypeName(),
            matchContext.getVisitorContext().getClassElement(Long.class).orElseThrow(), // Default result type
            interceptorMatch,
            true
        );

        if (result.isDto() && !result.isRuntimeDtoConversion()) {
            List<SourcePersistentProperty> dtoProjectionProperties = getDtoProjectionProperties(matchContext.getRootEntity(), resultType);
            if (!dtoProjectionProperties.isEmpty()) {
                List<Selection<?>> selectionList = dtoProjectionProperties.stream()
                    .map(p -> {
                        if (matchContext.getQueryBuilder().shouldAliasProjections()) {
                            return root.get(p.getName()).alias(p.getName());
                        } else {
                            return root.get(p.getName());
                        }
                    })
                    .collect(Collectors.toList());
                criteriaUpdate.returningMulti(
                    selectionList
                );
            }
        }

        AbstractPersistentEntityCriteriaUpdate<?> query = (AbstractPersistentEntityCriteriaUpdate<?>) criteriaQuery;

        boolean optimisticLock = query.hasVersionRestriction();

        final AnnotationMetadataHierarchy annotationMetadataHierarchy = new AnnotationMetadataHierarchy(
                matchContext.getRepositoryClass().getAnnotationMetadata(),
                matchContext.getAnnotationMetadata()
        );
        QueryBuilder queryBuilder = matchContext.getQueryBuilder();

        Map<String, Object> propertiesToUpdate = query.getUpdateValues();
        QueryModel queryModel = query.getQueryModel();
        QueryResult queryResult = queryBuilder.buildUpdate(annotationMetadataHierarchy, queryModel, propertiesToUpdate);

        return new MethodMatchInfo(
            getOperationType(),
            result.resultType(),
            interceptorType
        )
            .dto(result.isDto())
            .optimisticLock(optimisticLock)
            .queryResult(queryResult);
    }

    @Override
    protected DataMethod.OperationType getOperationType() {
        if (isReturning) {
            return DataMethod.OperationType.UPDATE_RETURNING;
        }
        return DataMethod.OperationType.UPDATE;
    }
}
