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
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.DataAnnotationUtils;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.annotation.Upsert;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.PersistentEntityUtils;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.model.query.builder.sql.Dialect;
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

import java.util.ArrayList;
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
        boolean producesAnEntity = doesMethodProduceEntityOrIterableOfEntity(methodElement);
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
        if (rootEntity.hasVersion()) {
            return "versioned entities are not supported";
        }
        List<String> conflictProperties = conflictProperties(matchContext);
        if (conflictProperties.isEmpty()) {
            if (!rootEntity.hasIdentity() && !rootEntity.hasCompositeIdentity()) {
                return "entity does not define an identity and no conflict properties were specified";
            }
            if (rootEntity.getIdentityProperties().stream().anyMatch(PersistentProperty::isGenerated)) {
                return "generated identity properties are not supported";
            }
        }
        return validateConflictProperties(rootEntity, conflictProperties);
    }

    @Nullable
    private String validateConflictProperties(SourcePersistentEntity rootEntity, List<String> conflictProperties) {
        for (String conflictProperty : conflictProperties) {
            if (StringUtils.isEmpty(conflictProperty) || StringUtils.isEmpty(conflictProperty.trim())) {
                return "conflict property cannot be blank";
            }
            PersistentPropertyPath propertyPath;
            try {
                propertyPath = rootEntity.getPropertyPath(conflictProperty);
            } catch (IllegalArgumentException e) {
                return "invalid conflict property path: " + conflictProperty;
            }
            if (propertyPath == null) {
                return "conflict property does not exist: " + conflictProperty;
            }
            List<PersistentProperty> generatedProperties = new ArrayList<>();
            PersistentEntityUtils.traversePersistentProperties(propertyPath, (associations, property) -> {
                if (property.isGenerated()) {
                    generatedProperties.add(property);
                }
            });
            if (!generatedProperties.isEmpty()) {
                return "generated conflict properties are not supported";
            }
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
            if (entityParameter != null && entitiesParameter != null) {
                throw new MatchFailedException("Cannot implement upsert method with both entity and iterable entity parameters", mc.getMethodElement());
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
            List<String> conflictProperties = conflictProperties(mc);
            boolean returnGeneratedId = shouldUseGeneratedIdReturning(mc, entityParameter);
            QueryResult queryResult = mc.getQueryBuilder().buildUpsert(annotationMetadataHierarchy, new QueryBuilder.UpsertQueryDefinition() {
                @Override
                public SourcePersistentEntity persistentEntity() {
                    return mc.getRootEntity();
                }

                @Override
                public List<String> conflictProperties() {
                    return conflictProperties;
                }

                @Override
                public boolean returnGeneratedId() {
                    return returnGeneratedId;
                }
            });

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

    private boolean shouldUseGeneratedIdReturning(MethodMatchContext matchContext,
                                                  @Nullable ParameterElement entityParameter) {
        boolean entityUpsert = entityParameter != null;
        SourcePersistentEntity rootEntity = matchContext.getRootEntity();
        if (!rootEntity.hasIdentity() || rootEntity.getIdentityProperties().stream().noneMatch(PersistentProperty::isGenerated)) {
            return false;
        }
        if (!(matchContext.getQueryBuilder() instanceof SqlQueryBuilder sqlQueryBuilder)) {
            return false;
        }
        Dialect dialect = sqlQueryBuilder.getDialect();
        if (dialect != Dialect.ORACLE && dialect != Dialect.SQL_SERVER) {
            return false;
        }
        if (TypeUtils.doesReturnVoid(matchContext.getMethodElement())) {
            return true;
        }
        ClassElement returnType = TypeUtils.getMethodProducingItemType(matchContext.getMethodElement());
        return returnType != null
            && (entityUpsert ? TypeUtils.isEntity(returnType) : producesEntityOrIterableOfEntity(returnType));
    }

    private boolean doesMethodProduceEntityOrIterableOfEntity(MethodElement methodElement) {
        return producesEntityOrIterableOfEntity(TypeUtils.getMethodProducingItemType(methodElement));
    }

    private boolean producesEntityOrIterableOfEntity(@Nullable ClassElement type) {
        return TypeUtils.isEntity(type) || TypeUtils.isIterableOfEntity(type);
    }

    private List<String> conflictProperties(MethodMatchContext matchContext) {
        return Arrays.asList(matchContext.getAnnotationMetadata().stringValues(Upsert.class, "conflictsOn"));
    }

}
