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
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.DataAnnotationUtils;
import io.micronaut.data.annotation.Insert;
import io.micronaut.data.annotation.Save;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaInsert;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaUpdate;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.impl.AbstractPersistentEntityCriteriaUpdate;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaBuilder;
import io.micronaut.data.processor.model.criteria.impl.MethodMatchSourcePersistentEntityCriteriaBuilderImpl;
import io.micronaut.data.processor.visitors.MatchFailedException;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.processing.ProcessingException;
import jakarta.persistence.criteria.Predicate;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A save method for saving a single entity.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class SaveMethodMatcher extends AbstractMethodMatcher {

    /**
     * The default constructor.
     */
    public SaveMethodMatcher() {
        super(MethodNameParser.builder()
            .match(QueryMatchId.PREFIX, "save", "persist", "store", "insert")
            .tryMatch(QueryMatchId.ALL_OR_ONE, ALL_OR_ONE)
            .tryMatchLastOccurrencePrefixed(QueryMatchId.RETURNING, null, RETURNING)
            .takeRest(QueryMatchId.PROJECTION)
            .build());
    }

    @Override
    @Nullable
    public MethodMatch match(MethodMatchContext matchContext) {
        if (matchContext.getMethodElement().hasStereotype(Insert.class) || matchContext.getMethodElement().hasStereotype(Save.class)) {
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
    protected MethodMatch match(MethodMatchContext matchContext, List<MethodNameParser.Match> matches) {
        MethodElement methodElement = matchContext.getMethodElement();
        boolean producesAnEntity = TypeUtils.doesMethodProducesAnEntityIterableOfAnEntity(methodElement);
        if (!TypeUtils.doesReturnVoid(methodElement)
            && !TypeUtils.doesMethodProducesANumber(methodElement)
            && !producesAnEntity) {
            ClassElement producingItem = TypeUtils.getMethodProducingItemType(methodElement);
            if (producingItem == null) {
                throw new ProcessingException(methodElement, "Unsupported return type for a save method: " + methodElement.getReturnType());
            }
            throw new ProcessingException(methodElement, "Unsupported return type for a save method: " + producingItem.getName());
        }
//        for (MethodNameParser.Match match : matches) {
            // Support Person savePerson(Person p) style methods
            // There are cases where the projection value is ignored: saveAndFlush
//            if (match.id() == QueryMatchId.PROJECTION && !match.part().equals(methodElement.getReturnType().getSimpleName())) {
//                throw new ProcessingException(methodElement, "Save method doesn't support projections");
//            }
//        }

        boolean isReturning = matches.stream().anyMatch(m -> m.id() == QueryMatchId.RETURNING);
        if (isReturning && !producesAnEntity) {
            throw new ProcessingException(methodElement, "Save method with a returning clause supports only entity/entities as a return type");
        }

        ParameterElement[] parameters = matchContext.getParameters();
        if (parameters.length == 0) {
            throw new ProcessingException(methodElement, "Save method requires parameters");
        }
        boolean saveOperation = isSaveOperation(methodElement, matches);
        if (matchContext.getParametersNotInRole().stream().allMatch(p -> TypeUtils.isIterableOfEntity(p.getGenericType()) || TypeUtils.isEntity(p.getGenericType()))) {
            return saveEntity(matchContext, isReturning ? DataMethod.OperationType.INSERT_RETURNING : DataMethod.OperationType.INSERT, saveOperation);
        }
        return saveProperties(saveOperation);
    }

    private boolean isSaveOperation(MethodElement methodElement, List<MethodNameParser.Match> matches) {
        if (methodElement.hasStereotype(Save.class)) {
            return true;
        }
        if (methodElement.hasStereotype(Insert.class)) {
            return false;
        }
        return matches.stream()
            .anyMatch(match -> match.id() == QueryMatchId.PREFIX && match.part().equals("save"));
    }

    public static MethodMatch saveEntity(MethodMatchContext matchContext, DataMethod.OperationType operationType, boolean saveOperation) {
        return mc -> {
            ParameterElement[] parameters = mc.getParameters();
            ParameterElement entityParameter = Arrays.stream(parameters).filter(p -> TypeUtils.isEntity(p.getGenericType())).findFirst().orElse(null);
            ParameterElement entitiesParameter = Arrays.stream(parameters).filter(p -> TypeUtils.isIterableOfEntity(p.getGenericType())).findFirst().orElse(null);
            if (entityParameter == null && entitiesParameter == null) {
                throw new MatchFailedException("Cannot implement save method for specified arguments and return type", mc.getMethodElement());
            }
            FindersUtils.InterceptorMatch entry = saveOperation
                ? FindersUtils.resolveSaveInterceptorType(entityParameter != null, entitiesParameter != null, mc)
                : FindersUtils.resolveInterceptorTypeByOperationType(
                    entityParameter != null,
                    entitiesParameter != null,
                    operationType, mc
                );
            MethodMatchInfo methodMatchInfo = new MethodMatchInfo(
                operationType,
                entry.returnType(),
                entry.interceptor()
            );
            if (!mc.supportsImplicitQueries()) {
                final AnnotationMetadataHierarchy annotationMetadataHierarchy = new AnnotationMetadataHierarchy(
                    mc.getRepositoryClass().getAnnotationMetadata(),
                    mc.getAnnotationMetadata()
                );
                boolean encodeEntityParameters = !DataAnnotationUtils.hasJsonEntityRepresentationAnnotation(mc.getAnnotationMetadata());
                SourcePersistentEntityCriteriaBuilder criteriaBuilder = new MethodMatchSourcePersistentEntityCriteriaBuilderImpl(matchContext);
                SourcePersistentEntity rootEntity = mc.getRootEntity();
                Objects.requireNonNull(rootEntity, "Root entity is required for save method");
                PersistentEntityCriteriaInsert<Object> criteriaInsert = criteriaBuilder.createCriteriaInsert(rootEntity);
                if (operationType == DataMethod.OperationType.INSERT_RETURNING) {
                    criteriaInsert.setReturning();
                }
                QueryResult queryResult = criteriaInsert.build(annotationMetadataHierarchy, mc.getQueryBuilder());
                methodMatchInfo
                    .encodeEntityParameters(encodeEntityParameters)
                    .queryResult(
                        queryResult
                );
                if (saveOperation && (rootEntity.hasIdentity() || rootEntity.hasCompositeIdentity())) {
                    boolean updateReturning = operationType == DataMethod.OperationType.INSERT_RETURNING;
                    MethodMatchInfo updateInfo = UpdateMethodMatcher.entityUpdate(List.of(), entityParameter, entitiesParameter, updateReturning)
                        .buildMatchInfo(mc);
                    QueryResult updateQueryResult = updateInfo.getQueryResult();
                    if (updateQueryResult != null) {
                        methodMatchInfo.addQueryResult(updateInfo.getOperationType(), updateInfo.getResultType(), updateQueryResult, true);
                    }
                }
            }
            if (entitiesParameter != null) {
                methodMatchInfo.addParameterRole(entitiesParameter, TypeRole.ENTITIES);
            }
            if (entityParameter != null) {
                methodMatchInfo.addParameterRole(entityParameter, TypeRole.ENTITY);
            }
            return methodMatchInfo;
        };
    }

    private MethodMatch saveProperties(boolean saveOperation) {
        return new MethodMatch() {

            @Override
            public MethodMatchInfo buildMatchInfo(MethodMatchContext matchContext) {
                List<ParameterElement> parameters = matchContext.getParametersNotInRole();
                SourcePersistentEntity rootEntity = matchContext.getRootEntity();
                Objects.requireNonNull(rootEntity, "Root entity is required for save method");
                ClassElement returnType = matchContext.getReturnType();
                if (TypeUtils.isReactiveOrFuture(returnType)) {
                    returnType = returnType.getFirstTypeArgument().orElse(null);
                }
                if (returnType == null || (!TypeUtils.isNumber(returnType) && !rootEntity.getName().equals(returnType.getName()))) {
                    throw new MatchFailedException("The return type of the save method must be the same as the root entity type: " + rootEntity.getName());
                }

                Set<String> requiredProps = rootEntity.getPersistentProperties()
                    .stream()
                    .filter(this::isRequiredProperty)
                    .map(PersistentProperty::getName)
                    .collect(Collectors.toSet());
                ParameterElement[] parameterElements = rootEntity.getClassElement().getPrimaryConstructor().map(MethodElement::getParameters).orElse(null);
                Map<String, ParameterElement> constructorArgs = new HashMap<>(10);
                if (ArrayUtils.isNotEmpty(parameterElements)) {
                    for (ParameterElement parameterElement : Objects.requireNonNull(parameterElements)) {
                        constructorArgs.put(getParameterValue(parameterElement), parameterElement);
                    }
                }
                for (ParameterElement parameter : parameters) {
                    String name = getParameterValue(parameter);
                    ClassElement type = parameter.getGenericType();

                    SourcePersistentProperty prop = rootEntity.getPropertyByName(name);
                    ParameterElement constructorArg = constructorArgs.get(name);
                    if (prop == null && constructorArg == null) {
                        throw new MatchFailedException("Cannot save with non-existent property or constructor argument: " + name);
                    }

                    if (prop != null) {
                        String typeName = prop.getTypeName();
                        if (!type.isAssignable(typeName) && !typeName.equals(type.getName())) {
                            throw new MatchFailedException("Type mismatch. Found parameter of type [" + type.getName() + "]. Required property of type: " + typeName);
                        }
                        requiredProps.remove(name);
                    } else if (constructorArg != null) {
                        ClassElement argType = constructorArg.getGenericType();
                        String typeName = argType.getName();
                        if (!type.isAssignable(typeName) && !typeName.equals(type.getName())) {
                            throw new MatchFailedException("Type mismatch. Found parameter of type [" + type.getName() + "]. Required constructor argument of: " + typeName);
                        }
                    }
                    constructorArgs.remove(name);
                }

                if (!requiredProps.isEmpty()) {
                    throw new MatchFailedException("Save method missing required properties: " + requiredProps);
                }
                if (!constructorArgs.isEmpty()) {
                    Collection<ParameterElement> values = constructorArgs.values();
                    Set<String> names = values.stream().filter(pe -> {
                        SourcePersistentProperty prop = rootEntity.getPropertyByName(pe.getName());
                        return prop != null && prop.isRequired() && !prop.getType().isPrimitive();
                    }).map(p -> getParameterValue(p)).collect(Collectors.toSet());
                    if (CollectionUtils.isNotEmpty(names)) {
                        throw new MatchFailedException("Save method missing required constructor arguments: " + names);
                    }
                }

                final AnnotationMetadataHierarchy annotationMetadataHierarchy = new AnnotationMetadataHierarchy(
                    matchContext.getRepositoryClass().getAnnotationMetadata(),
                    matchContext.getAnnotationMetadata()
                );

                SourcePersistentEntityCriteriaBuilder criteriaBuilder = new MethodMatchSourcePersistentEntityCriteriaBuilderImpl(matchContext);
                FindersUtils.InterceptorMatch e = saveOperation
                    ? FindersUtils.pickSaveOneInterceptor(matchContext, matchContext.getReturnType())
                    : FindersUtils.pickInsertOneInterceptor(matchContext, matchContext.getReturnType());
                boolean encodeEntityParameters = !DataAnnotationUtils.hasJsonEntityRepresentationAnnotation(matchContext.getAnnotationMetadata());
                MethodMatchInfo methodMatchInfo = new MethodMatchInfo(
                    DataMethod.OperationType.INSERT,
                    e.returnType(),
                    e.interceptor()
                )
                    .encodeEntityParameters(encodeEntityParameters)
                    .queryResult(
                        criteriaBuilder.createCriteriaInsert(matchContext.getRootEntity()).build(annotationMetadataHierarchy, matchContext.getQueryBuilder())
                    );
                if (saveOperation) {
                    addPropertyUpdateQueryIfPossible(methodMatchInfo, matchContext, rootEntity, parameters, annotationMetadataHierarchy, e.returnType());
                }
                return methodMatchInfo;
            }

            private boolean isRequiredProperty(SourcePersistentProperty pp) {
                return pp.isRequired() &&
                    ClassUtils.getPrimitiveType(pp.getTypeName()).isEmpty();
            }

        };
    }

    private void addPropertyUpdateQueryIfPossible(MethodMatchInfo methodMatchInfo,
                                                  MethodMatchContext matchContext,
                                                  SourcePersistentEntity rootEntity,
                                                  List<ParameterElement> parameters,
                                                  AnnotationMetadataHierarchy annotationMetadataHierarchy,
                                                  ClassElement resultType) {
        Map<SourcePersistentProperty, ParameterElement> identityParameters = findIdentityParameters(rootEntity, parameters);
        if (identityParameters.isEmpty()) {
            return;
        }
        SourcePersistentEntityCriteriaBuilder criteriaBuilder = new MethodMatchSourcePersistentEntityCriteriaBuilderImpl(matchContext);
        PersistentEntityCriteriaUpdate<Object> criteriaUpdate = criteriaBuilder.createCriteriaUpdate(null);
        PersistentEntityRoot<Object> root = criteriaUpdate.from(rootEntity);

        List<Predicate> predicates = new ArrayList<>(identityParameters.size() + 1);
        for (Map.Entry<SourcePersistentProperty, ParameterElement> entry : identityParameters.entrySet()) {
            SourcePersistentProperty identity = entry.getKey();
            ParameterElement identityParameter = entry.getValue();
            PersistentPropertyPath identityPath = new PersistentPropertyPath(identity);
            if (rootEntity.hasIdentity()) {
                predicates.add(criteriaBuilder.equal(root.id(), criteriaBuilder.parameter(identityParameter, identityPath)));
            } else {
                predicates.add(criteriaBuilder.equal(root.get(identity.getName()), criteriaBuilder.parameter(identityParameter, identityPath)));
            }
        }

        ParameterElement versionParameter = null;
        if (rootEntity.hasVersion()) {
            versionParameter = findParameterForProperty(parameters, rootEntity.getVersion());
            if (versionParameter != null) {
                predicates.add(criteriaBuilder.equal(root.version(), criteriaBuilder.parameter(versionParameter, new PersistentPropertyPath(rootEntity.getVersion()))));
            }
        }
        criteriaUpdate.where(predicates.toArray(Predicate[]::new));

        Set<SourcePersistentProperty> identities = identityParameters.keySet();
        for (ParameterElement parameter : parameters) {
            SourcePersistentProperty property = rootEntity.getPropertyByName(getParameterValue(parameter));
            if (property != null
                && !identities.contains(property)
                && (!rootEntity.hasVersion() || !property.equals(rootEntity.getVersion()))
                && !property.isGenerated()) {
                criteriaUpdate.set(property.getName(), criteriaBuilder.parameter(parameter, new PersistentPropertyPath(property)));
            }
        }

        rootEntity.getPersistentProperties().stream()
            .filter(p -> p.findAnnotation(AutoPopulated.class).map(ap -> ap.getRequiredValue(AutoPopulated.UPDATABLE, Boolean.class)).orElse(false))
            .forEach(p -> criteriaUpdate.set(p.getName(), criteriaBuilder.parameter(null, new PersistentPropertyPath(p))));

        if (versionParameter != null && !rootEntity.getVersion().isGenerated()) {
            criteriaUpdate.set(rootEntity.getVersion().getName(), criteriaBuilder.parameter(null, new PersistentPropertyPath(rootEntity.getVersion())));
        }

        AbstractPersistentEntityCriteriaUpdate<Object> update = (AbstractPersistentEntityCriteriaUpdate<Object>) criteriaUpdate;
        if (update.getUpdateValues().isEmpty()) {
            Map.Entry<SourcePersistentProperty, ParameterElement> firstIdentity = identityParameters.entrySet().iterator().next();
            SourcePersistentProperty identity = firstIdentity.getKey();
            criteriaUpdate.set(identity.getName(), criteriaBuilder.parameter(firstIdentity.getValue(), new PersistentPropertyPath(identity)));
        }

        QueryResult queryResult = criteriaUpdate.build(annotationMetadataHierarchy, matchContext.getQueryBuilder());
        if (queryResult != null) {
            methodMatchInfo.addQueryResult(DataMethod.OperationType.UPDATE, resultType, queryResult, true);
        }
    }

    private Map<SourcePersistentProperty, ParameterElement> findIdentityParameters(SourcePersistentEntity rootEntity, List<ParameterElement> parameters) {
        if (!rootEntity.hasIdentity() && !rootEntity.hasCompositeIdentity()) {
            return Map.of();
        }
        SourcePersistentProperty[] identities = rootEntity.hasIdentity()
            ? new SourcePersistentProperty[] { rootEntity.getIdentity() }
            : rootEntity.getCompositeIdentity();
        Map<SourcePersistentProperty, ParameterElement> identityParameters = new LinkedHashMap<>(identities.length);
        for (SourcePersistentProperty identity : identities) {
            ParameterElement identityParameter = findParameterForProperty(parameters, identity);
            if (identityParameter == null) {
                // The update alternative can only be built when every identity property is supplied.
                return Map.of();
            }
            identityParameters.put(identity, identityParameter);
        }
        return identityParameters;
    }

    @Nullable
    private ParameterElement findParameterForProperty(List<ParameterElement> parameters, SourcePersistentProperty property) {
        for (ParameterElement parameter : parameters) {
            if (getParameterValue(parameter).equals(property.getName())) {
                return parameter;
            }
        }
        return null;
    }

    private String getParameterValue(ParameterElement p) {
        return p.stringValue(Parameter.class).orElseGet(p::getName);
    }

}
