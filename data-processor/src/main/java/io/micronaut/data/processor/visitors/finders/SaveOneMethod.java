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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.intercept.DataInterceptor;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import io.micronaut.data.processor.visitors.MatchContext;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A save method for saving a single entity.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class SaveOneMethod extends AbstractPatternBasedMethod {

    /**
     * Default constructor.
     */
    public SaveOneMethod() {
        super(SaveEntityMethod.METHOD_PATTERN);
    }

    @NonNull
    @Override
    protected MethodMatchInfo.OperationType getOperationType() {
        return MethodMatchInfo.OperationType.INSERT;
    }

    @Override
    public boolean isMethodMatch(@NonNull MethodElement methodElement, @NonNull MatchContext matchContext) {
        ParameterElement[] parameters = matchContext.getParameters();
        return super.isMethodMatch(methodElement, matchContext) && isValidSaveReturnType(matchContext, true) &&
                parameters.length > 0 &&
                !TypeUtils.isIterableOfEntity(parameters[0].getGenericType());
    }

    @Nullable
    @Override
    public MethodMatchInfo buildMatchInfo(@NonNull MethodMatchContext matchContext) {
        List<ParameterElement> parameters = matchContext.getParametersNotInRole();
        SourcePersistentEntity rootEntity = matchContext.getRootEntity();
        ClassElement returnType = matchContext.getReturnType();
        if (TypeUtils.isReactiveOrFuture(returnType)) {
            returnType = returnType.getFirstTypeArgument().orElse(null);
        }
        if (returnType == null || !TypeUtils.isNumber(returnType) && !rootEntity.getName().equals(returnType.getName())) {
            matchContext.fail("The return type of the save method must be the same as the root entity type: " + rootEntity.getName());
            return null;
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
                constructorArgs.put(parameterElement.getName(), parameterElement);
            }
        }
        for (ParameterElement parameter : parameters) {
            String name = parameter.getName();
            ClassElement type = parameter.getGenericType();

            SourcePersistentProperty prop = rootEntity.getPropertyByName(name);
            ParameterElement constructorArg = constructorArgs.get(name);
            if (prop == null && constructorArg == null) {
                matchContext.fail("Cannot save with non-existent property or constructor argument: " + name);
                return null;
            }

            if (prop != null) {
                String typeName = prop.getTypeName();
                if (!type.isAssignable(typeName) && !typeName.equals(type.getName())) {
                    matchContext.fail("Type mismatch. Found parameter of type [" + type.getName() + "]. Required property of type: " + typeName);
                    return null;
                }
                requiredProps.remove(name);
                constructorArgs.remove(name);
            } else {
                ClassElement argType = constructorArg.getGenericType();
                String typeName = argType.getName();
                if (!type.isAssignable(typeName) && !typeName.equals(type.getName())) {
                    matchContext.fail("Type mismatch. Found parameter of type [" + type.getName() + "]. Required constructor argument of: " + typeName);
                    return null;
                }
                constructorArgs.remove(name);
            }
        }

        if (!requiredProps.isEmpty()) {
            matchContext.fail("Save method missing required properties: " + requiredProps);
            return null;
        }
        if (!constructorArgs.isEmpty()) {
            Collection<ParameterElement> values = constructorArgs.values();
            Set<String> names = values.stream().filter(pe -> {
                SourcePersistentProperty prop = rootEntity.getPropertyByName(pe.getName());
                return prop != null && prop.isRequired() && !prop.getType().isPrimitive();
            }).map(ParameterElement::getName).collect(Collectors.toSet());
            if (CollectionUtils.isNotEmpty(names)) {
                matchContext.fail("Save method missing required constructor arguments: " + names);
                return null;
            }
        }

        Map.Entry<ClassElement, Class<? extends DataInterceptor>> e = FindersUtils.pickSaveOneInterceptor(matchContext, matchContext.getReturnType());
        return new MethodMatchInfo(
                e.getKey(),
                QueryModel.from(matchContext.getRootEntity()),
                getInterceptorElement(matchContext, e.getValue()),
                MethodMatchInfo.OperationType.INSERT
        );
    }

    private boolean isRequiredProperty(SourcePersistentProperty pp) {
        return pp.isRequired() &&
                !ClassUtils.getPrimitiveType(pp.getTypeName()).isPresent();
    }

    /**
     * Is the return type valid for saving an entity.
     * @param matchContext The match context
     * @param entityArgumentNotRequired  If an entity arg is not required
     * @return True if it is
     */
    private static boolean isValidSaveReturnType(@NonNull MatchContext matchContext, boolean entityArgumentNotRequired) {
        ClassElement returnType = matchContext.getReturnType();
        if (TypeUtils.isVoid(returnType) || TypeUtils.isNumber(returnType)) {
            return true;
        }
        if (TypeUtils.isReactiveOrFuture(returnType)) {
            returnType = returnType.getFirstTypeArgument().orElse(null);
        }
        if (returnType == null) {
            return false;
        }
        if (TypeUtils.isNumber(returnType)) {
            return true;
        }
        return returnType.hasAnnotation(MappedEntity.class) &&
                (entityArgumentNotRequired || returnType.getName().equals(matchContext.getParameters()[0].getGenericType().getName()));
    }

}
