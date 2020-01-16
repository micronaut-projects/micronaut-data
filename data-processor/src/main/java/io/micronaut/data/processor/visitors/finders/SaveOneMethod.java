/*
 * Copyright 2017-2019 original authors
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

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.intercept.DataInterceptor;
import io.micronaut.data.intercept.SaveOneInterceptor;
import io.micronaut.data.intercept.async.SaveOneAsyncInterceptor;
import io.micronaut.data.intercept.reactive.SaveOneReactiveInterceptor;
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

    @Override
    public boolean isMethodMatch(@NonNull MethodElement methodElement, @NonNull MatchContext matchContext) {
        ParameterElement[] parameters = matchContext.getParameters();
        return super.isMethodMatch(methodElement, matchContext) &&
                SaveEntityMethod.isValidSaveReturnType(matchContext, true) &&
                parameters.length > 0 &&
                !TypeUtils.isIterableOfEntity(parameters[0].getGenericType()) ;
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
        if (returnType == null || !rootEntity.getName().equals(returnType.getName())) {
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

        final Class<? extends DataInterceptor> interceptor =
                pickSaveInterceptor(matchContext.getReturnType());
        if (matchContext.supportsImplicitQueries()) {
            return new MethodMatchInfo(
                    returnType,
                    null,
                    getInterceptorElement(matchContext, interceptor),
                    MethodMatchInfo.OperationType.INSERT
            );
        } else {
            return new MethodMatchInfo(
                    returnType,
                    QueryModel.from(matchContext.getRootEntity()),
                    getInterceptorElement(matchContext, interceptor),
                    MethodMatchInfo.OperationType.INSERT
            );
        }


    }

    private boolean isRequiredProperty(SourcePersistentProperty pp) {
        return pp.isRequired() &&
                !ClassUtils.getPrimitiveType(pp.getTypeName()).isPresent();
    }


    /**
     * Pick a runtime interceptor to use based on the return type.
     * @param returnType The return type
     * @return The interceptor
     */
    private static @NonNull Class<? extends DataInterceptor> pickSaveInterceptor(@NonNull ClassElement returnType) {
        Class<? extends DataInterceptor> interceptor;
        if (TypeUtils.isFutureType(returnType)) {
            interceptor = SaveOneAsyncInterceptor.class;
        } else if (TypeUtils.isReactiveOrFuture(returnType)) {
            interceptor = SaveOneReactiveInterceptor.class;
        } else {
            interceptor = SaveOneInterceptor.class;
        }
        return interceptor;
    }
}
