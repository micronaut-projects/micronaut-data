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
package io.micronaut.data.processor.visitors.finders;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.annotation.DataAnnotationUtils;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.annotation.Upsert;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.visitors.MatchFailedException;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.processing.ProcessingException;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * Upsert method matcher.
 *
 * @since 5.1.0
 */
@Internal
public final class UpsertMethodMatcher extends AbstractMethodMatcher {

    /**
     * The default constructor.
     */
    public UpsertMethodMatcher() {
        super(MethodNameParser.builder()
            .match(QueryMatchId.PREFIX, "upsert")
            .tryMatch(QueryMatchId.ALL_OR_ONE, ALL)
            .build());
    }

    @Override
    @Nullable
    public MethodMatch match(MethodMatchContext matchContext) {
        if (matchContext.getMethodElement().hasStereotype(Upsert.class)) {
            if (!matchContext.hasRootEntity()) {
                matchContext.findImplicitRootEntity();
            }
            if (!matchContext.hasRootEntity()) {
                throw new ProcessingException(matchContext.getMethodElement(), "Repository does not have a well-defined primary entity type");
            }
            return match(matchContext, List.of());
        }
        return super.match(matchContext);
    }

    @Override
    @Nullable
    protected MethodMatch match(MethodMatchContext matchContext, List<MethodNameParser.Match> matches) {
        if (!(matchContext.getQueryBuilder() instanceof SqlQueryBuilder) || matchContext.supportsImplicitQueries()) {
            return null;
        }
        MethodElement methodElement = matchContext.getMethodElement();
        boolean producesAnEntity = TypeUtils.doesMethodProducesAnEntityIterableOfAnEntity(methodElement);
        if (!TypeUtils.doesReturnVoid(methodElement)
            && !TypeUtils.doesMethodProducesANumber(methodElement)
            && !producesAnEntity) {
            ClassElement producingItem = TypeUtils.getMethodProducingItemType(methodElement);
            if (producingItem == null) {
                throw new ProcessingException(methodElement, "Unsupported return type for an upsert method: " + methodElement.getReturnType());
            }
            throw new ProcessingException(methodElement, "Unsupported return type for an upsert method: " + producingItem.getName());
        }

        if (matchContext.getParameters().length == 0) {
            throw new ProcessingException(methodElement, "Upsert method requires parameters");
        }
        if (matchContext.getParametersNotInRole().stream().allMatch(p -> TypeUtils.isIterableOfEntity(p.getGenericType()) || TypeUtils.isEntity(p.getGenericType()))) {
            String unsupportedReason = explicitUpsertUnsupportedReason(matchContext);
            if (unsupportedReason != null) {
                throw new ProcessingException(methodElement, "Cannot implement explicit upsert query: " + unsupportedReason);
            }
            return upsertEntity();
        }
        throw new MatchFailedException("Cannot implement upsert method for specified arguments and return type", methodElement);
    }

    @Nullable
    private String explicitUpsertUnsupportedReason(MethodMatchContext matchContext) {
        if (!matchContext.hasRootEntity()) {
            return "repository does not have a well-defined primary entity type";
        }
        SourcePersistentEntity rootEntity = matchContext.getRootEntity();
        if (DataAnnotationUtils.hasJsonEntityRepresentationAnnotation(matchContext.getAnnotationMetadata())) {
            return "JSON entity representation is not supported";
        }
        if (DataAnnotationUtils.hasJsonEntityRepresentationAnnotation(rootEntity.getAnnotationMetadata())) {
            return "JSON entity representation is not supported";
        }
        if (!rootEntity.hasIdentity() && !rootEntity.hasCompositeIdentity()) {
            return "entity does not define an identity";
        }
        if (rootEntity.hasVersion()) {
            return "versioned entities are not supported";
        }
        if (rootEntity.getIdentityProperties().stream().anyMatch(PersistentProperty::isGenerated)) {
            return "generated identity properties are not supported";
        }
        return null;
    }

    private MethodMatch upsertEntity() {
        return mc -> {
            ParameterElement[] parameters = mc.getParameters();
            ParameterElement entityParameter = Arrays.stream(parameters).filter(p -> TypeUtils.isEntity(p.getGenericType())).findFirst().orElse(null);
            ParameterElement entitiesParameter = Arrays.stream(parameters).filter(p -> TypeUtils.isIterableOfEntity(p.getGenericType())).findFirst().orElse(null);
            if (entityParameter == null && entitiesParameter == null) {
                throw new MatchFailedException("Cannot implement upsert method for specified arguments and return type", mc.getMethodElement());
            }

            FindersUtils.InterceptorMatch entry = FindersUtils.resolveInterceptorTypeByOperationType(
                entityParameter != null,
                entitiesParameter != null,
                DataMethod.OperationType.UPSERT,
                mc
            );
            MethodMatchInfo methodMatchInfo = new MethodMatchInfo(
                DataMethod.OperationType.UPSERT,
                entry.returnType(),
                entry.interceptor()
            );

            AnnotationMetadataHierarchy annotationMetadataHierarchy = new AnnotationMetadataHierarchy(
                mc.getRepositoryClass().getAnnotationMetadata(),
                mc.getAnnotationMetadata()
            );
            QueryResult queryResult = mc.getQueryBuilder().buildUpsert(annotationMetadataHierarchy, mc::getRootEntity);

            methodMatchInfo
                .encodeEntityParameters(true)
                .queryResult(queryResult);
            if (entitiesParameter != null) {
                methodMatchInfo.addParameterRole(entitiesParameter, TypeRole.ENTITIES);
            }
            if (entityParameter != null) {
                methodMatchInfo.addParameterRole(entityParameter, TypeRole.ENTITY);
            }
            return methodMatchInfo;
        };
    }

}
