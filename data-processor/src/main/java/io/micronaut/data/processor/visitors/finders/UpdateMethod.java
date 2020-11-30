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
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.annotation.Version;
import io.micronaut.data.intercept.DataInterceptor;
import io.micronaut.data.intercept.UpdateInterceptor;
import io.micronaut.data.intercept.async.UpdateAsyncInterceptor;
import io.micronaut.data.intercept.reactive.UpdateReactiveInterceptor;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.query.QueryParameter;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import io.micronaut.data.processor.visitors.MatchContext;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.ast.*;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Support for simple updates by ID.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class UpdateMethod extends AbstractPatternBasedMethod {

    /**
     * Default constructor.
     */
    public UpdateMethod() {
        super(Pattern.compile("^update\\w*$"));
    }

    @NonNull
    @Override
    protected MethodMatchInfo.OperationType getOperationType() {
        return MethodMatchInfo.OperationType.UPDATE;
    }

    @Override
    public boolean isMethodMatch(MethodElement methodElement, MatchContext matchContext) {
        boolean isMatch = super.isMethodMatch(methodElement, matchContext) &&
                methodElement.getParameters().length > 1 &&
                hasIdParameter(methodElement.getParameters());
        if (isMatch) {
            if (!TypeUtils.isValidBatchUpdateReturnType(methodElement)) {
                matchContext.possiblyFail("Update methods only support void or number based return types");
            } else {
                return true;
            }
        }
        return false;
    }

    private boolean hasIdParameter(ParameterElement[] parameters) {
        return Arrays.stream(parameters).anyMatch(p ->
                p.hasAnnotation(Id.class)
        );
    }

    @Nullable
    @Override
    public MethodMatchInfo buildMatchInfo(
            @NonNull MethodMatchContext matchContext) {
        List<ParameterElement> parameters = matchContext.getParametersNotInRole();
        List<ParameterElement> remainingParameters = parameters.stream()
                .filter(p -> !p.hasAnnotation(Id.class) && !p.hasAnnotation(Version.class))
                .collect(Collectors.toList());

        ParameterElement idParameter = parameters.stream().filter(p -> p.hasAnnotation(Id.class)).findFirst()
                .orElse(null);
        if (idParameter == null) {
            matchContext.fail("ID required for update method, but not specified");
            return null;
        }

        SourcePersistentEntity entity = matchContext.getRootEntity();
        List<String> properiesToUpdate = new ArrayList<>(remainingParameters.size());

        ParameterElement versionMatchMethodParameter = null;
        SourcePersistentProperty version = entity.getVersion();
        if (version != null) {
            versionMatchMethodParameter = parameters.stream().filter(p -> p.hasAnnotation(Version.class)).findFirst().orElse(null);
            if (versionMatchMethodParameter != null) {
                properiesToUpdate.add(version.getName());
            }
        }

        SourcePersistentProperty identity = entity.getIdentity();
        if (identity == null) {
            matchContext.fail("Cannot update by ID for entity that has no ID");
            return null;
        } else {
            String idType = TypeUtils.getTypeName(identity.getType());
            String idParameterType = TypeUtils.getTypeName(idParameter.getType());
            if (!idType.equals(idParameterType)) {
                matchContext.fail("ID type of method [" + idParameterType + "] does not match ID type of entity: " + idType);
            }
        }


        QueryModel query = QueryModel.from(entity);
        query.idEq(new QueryParameter(idParameter.getName()));
        if (versionMatchMethodParameter != null) {
            query.versionEq(new QueryParameter(VERSION_MATCH_PARAMETER));
        }
        for (ParameterElement parameter : remainingParameters) {
            String name = parameter.stringValue(Parameter.class).orElse(parameter.getName());
            SourcePersistentProperty prop = entity.getPropertyByName(name);
            if (prop == null) {
                matchContext.fail("Cannot update non-existent property: " + name);
                return null;
            } else {
                if (prop.isGenerated()) {
                    matchContext.fail("Cannot update a generated property: " + name);
                    return null;
                } else {
                    properiesToUpdate.add(name);
                }
            }
        }

        Element lastUpdatedProperty = matchContext.getParametersInRole().get(TypeRole.LAST_UPDATED_PROPERTY);
        if (lastUpdatedProperty instanceof PropertyElement) {
            properiesToUpdate.add(lastUpdatedProperty.getName());
        }

        ClassElement returnType = matchContext.getReturnType();
        Class<? extends DataInterceptor> interceptor = pickUpdateInterceptor(returnType);
        if (TypeUtils.isReactiveOrFuture(returnType)) {
            returnType = returnType.getGenericType().getFirstTypeArgument().orElse(returnType);
        }
        MethodMatchInfo info = new MethodMatchInfo(
                returnType,
                query,
                getInterceptorElement(matchContext, interceptor),
                MethodMatchInfo.OperationType.UPDATE,
                properiesToUpdate.toArray(new String[0])
        );

        info.addParameterRole(
                TypeRole.ID,
                idParameter.getName()
        );
        if (versionMatchMethodParameter != null) {
            info.setOptimisticLock(true);
            info.addParameterRole(
                    TypeRole.VERSION_UPDATE,
                    VERSION_UPDATE_PARAMETER
            );
            info.addParameterRole(
                    TypeRole.VERSION_MATCH,
                    versionMatchMethodParameter.getName()
            );
            info.addQueryToMethodParameterBinding(VERSION_MATCH_PARAMETER, versionMatchMethodParameter.getName());
            info.addQueryToMethodParameterBinding(version.getName(), VERSION_UPDATE_PARAMETER);
        }
        return info;
    }

    /**
     * Pick the correct delete all interceptor.
     * @param returnType The return type
     * @return The interceptor
     */
    static Class<? extends DataInterceptor> pickUpdateInterceptor(ClassElement returnType) {
        Class<? extends DataInterceptor> interceptor;
        if (TypeUtils.isFutureType(returnType)) {
            interceptor = UpdateAsyncInterceptor.class;
        } else if (TypeUtils.isReactiveType(returnType)) {
            interceptor = UpdateReactiveInterceptor.class;
        } else {
            interceptor = UpdateInterceptor.class;
        }
        return interceptor;
    }
}
