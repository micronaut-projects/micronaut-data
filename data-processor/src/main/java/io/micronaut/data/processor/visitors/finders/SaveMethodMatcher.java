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
import io.micronaut.data.annotation.DataAnnotationUtils;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import io.micronaut.data.processor.visitors.MatchFailedException;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.processing.ProcessingException;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    protected MethodMatch match(MethodMatchContext matchContext, List<MethodNameParser.Match> matches) {
        MethodElement methodElement = matchContext.getMethodElement();
        boolean producesAnEntity = TypeUtils.doesMethodProducesAnEntityIterableOfAnEntity(methodElement);
        if (!TypeUtils.doesReturnVoid(methodElement)
            && !TypeUtils.doesMethodProducesANumber(methodElement)
            && !producesAnEntity) {
            ClassElement producingItem = TypeUtils.getMethodProducingItemType(methodElement);
            throw new ProcessingException(methodElement, "Unsupported return type for a save method: " + producingItem.getName());
        }
        for (MethodNameParser.Match match : matches) {
            // Support Person savePerson(Person p) style methods
            if (match.id() == QueryMatchId.PROJECTION && !match.part().equals(methodElement.getReturnType().getSimpleName())) {
                throw new ProcessingException(methodElement, "Save method doesn't support projections");
            }
        }

        boolean isReturning = matches.stream().anyMatch(m -> m.id() == QueryMatchId.RETURNING);
        if (isReturning && !producesAnEntity) {
            throw new ProcessingException(methodElement, "Save method with a returning clause supports only entity/entities as a return type");
        }

        ParameterElement[] parameters = matchContext.getParameters();
        if (parameters.length == 0) {
            throw new ProcessingException(methodElement, "Save method requires parameters");
        }
        if (matchContext.getParametersNotInRole().stream().allMatch(p -> TypeUtils.isIterableOfEntity(p.getGenericType()) || TypeUtils.isEntity(p.getGenericType()))) {
            return saveEntity(isReturning ? DataMethod.OperationType.INSERT_RETURNING : DataMethod.OperationType.INSERT);
        }
        return saveProperties();
    }

    private MethodMatch saveEntity(DataMethod.OperationType operationType) {
        return mc -> {
            ParameterElement[] parameters = mc.getParameters();
            ParameterElement entityParameter = Arrays.stream(parameters).filter(p -> TypeUtils.isEntity(p.getGenericType())).findFirst().orElse(null);
            ParameterElement entitiesParameter = Arrays.stream(parameters).filter(p -> TypeUtils.isIterableOfEntity(p.getGenericType())).findFirst().orElse(null);
            if (entityParameter == null && entitiesParameter == null) {
                throw new MatchFailedException("Cannot implement save method for specified arguments and return type", mc.getMethodElement());
            }
            FindersUtils.InterceptorMatch entry = FindersUtils.resolveInterceptorTypeByOperationType(
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
                QueryResult queryResult;
                if (operationType == DataMethod.OperationType.INSERT_RETURNING) {
                    queryResult = mc.getQueryBuilder().buildInsertReturning(annotationMetadataHierarchy, mc.getRootEntity());
                } else {
                    queryResult = mc.getQueryBuilder().buildInsert(annotationMetadataHierarchy, mc.getRootEntity());
                }
                methodMatchInfo
                    .encodeEntityParameters(encodeEntityParameters)
                    .queryResult(
                        queryResult
                    );
            }
            if (entitiesParameter != null) {
                methodMatchInfo.addParameterRole(TypeRole.ENTITIES, entitiesParameter.getName());
            }
            if (entityParameter != null) {
                methodMatchInfo.addParameterRole(TypeRole.ENTITY, entityParameter.getName());
            }
            return methodMatchInfo;
        };
    }

    private MethodMatch saveProperties() {
        return new MethodMatch() {

            @Override
            public MethodMatchInfo buildMatchInfo(MethodMatchContext matchContext) {
                List<ParameterElement> parameters = matchContext.getParametersNotInRole();
                SourcePersistentEntity rootEntity = matchContext.getRootEntity();
                ClassElement returnType = matchContext.getReturnType();
                if (TypeUtils.isReactiveOrFuture(returnType)) {
                    returnType = returnType.getFirstTypeArgument().orElse(null);
                }
                if (returnType == null || !TypeUtils.isNumber(returnType) && !rootEntity.getName().equals(returnType.getName())) {
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
                    for (ParameterElement parameterElement : parameterElements) {
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
                    } else {
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

                FindersUtils.InterceptorMatch e = FindersUtils.pickSaveOneInterceptor(matchContext, matchContext.getReturnType());
                boolean encodeEntityParameters = !DataAnnotationUtils.hasJsonEntityRepresentationAnnotation(matchContext.getAnnotationMetadata());
                return new MethodMatchInfo(
                    DataMethod.OperationType.INSERT,
                    e.returnType(),
                    e.interceptor()
                )
                    .encodeEntityParameters(encodeEntityParameters)
                    .queryResult(
                        matchContext.getQueryBuilder().buildInsert(annotationMetadataHierarchy, matchContext.getRootEntity())
                    );
            }

            private boolean isRequiredProperty(SourcePersistentProperty pp) {
                return pp.isRequired() &&
                    ClassUtils.getPrimitiveType(pp.getTypeName()).isEmpty();
            }

        };
    }

    private String getParameterValue(ParameterElement p) {
        return p.stringValue(Parameter.class).orElseGet(p::getName);
    }

}
