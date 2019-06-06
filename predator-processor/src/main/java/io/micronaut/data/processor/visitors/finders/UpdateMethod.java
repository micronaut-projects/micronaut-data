/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.processor.visitors.finders;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.intercept.PredatorInterceptor;
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

    @Override
    public boolean isMethodMatch(MethodElement methodElement, MatchContext matchContext) {
        return super.isMethodMatch(methodElement, matchContext) &&
                methodElement.getParameters().length > 1 &&
                TypeUtils.isValidBatchUpdateReturnType(methodElement) &&
                hasIdParameter(methodElement.getParameters());
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
        ParameterElement[] parameters = matchContext.getMethodElement().getParameters();
        List<ParameterElement> remainingParameters = Arrays.stream(parameters)
                .filter(p -> !p.hasAnnotation(Id.class))
                .collect(Collectors.toList());

        ParameterElement idParameter = Arrays.stream(parameters).filter(p -> p.hasAnnotation(Id.class)).findFirst()
                .orElse(null);
        if (idParameter == null) {
            matchContext.fail("ID required for update method, but not specified");
            return null;
        }

        SourcePersistentEntity entity = matchContext.getRootEntity();

        SourcePersistentProperty identity = entity.getIdentity();
        if (identity == null) {
            matchContext.fail("Cannot update by ID for entity that has no ID");
            return null;
        } else {
            ClassElement idType = identity.getType();
            ClassElement idParameterType = idParameter.getType();
            if (idType == null || idParameterType == null) {
                matchContext.fail("Unsupported ID type");
                return null;
            } else if (idType.equals(idParameterType)) {
                matchContext.fail("ID type of method [" + idParameterType.getName() + "] does not match ID type of entity: " + idType.getName());
            }
        }


        QueryModel query = QueryModel.from(entity);
        query.idEq(new QueryParameter(idParameter.getName()));
        List<String> properiesToUpdate = new ArrayList<>(remainingParameters.size());
        for (ParameterElement parameter : remainingParameters) {
            String name = parameter.getName();
            SourcePersistentProperty prop = entity.getPropertyByName(name);
            if (prop == null) {
                matchContext.fail("Cannot update non-existent property" + name);
                return null;
            } else {
                if (prop.isGenerated()) {
                    matchContext.fail("Cannot update a generated property" + name);
                    return null;
                } else {
                    properiesToUpdate.add(name);
                }
            }
        }

        Element element = matchContext.getParametersInRole().get(TypeRole.LAST_UPDATED_PROPERTY);
        if (element instanceof PropertyElement) {
            properiesToUpdate.add(element.getName());
        }

        ClassElement returnType = matchContext.getReturnType();
        MethodMatchInfo info = new MethodMatchInfo(
                returnType,
                query,
                pickUpdateInterceptor(returnType),
                MethodMatchInfo.OperationType.UPDATE,
                properiesToUpdate.toArray(new String[0])
        );

        info.addParameterRole(
                TypeRole.ID,
                idParameter.getName()
        );
        return info;
    }

    /**
     * Pick the correct delete all interceptor.
     * @param returnType The return type
     * @return The interceptor
     */
    static Class<? extends PredatorInterceptor> pickUpdateInterceptor(ClassElement returnType) {
        Class<? extends PredatorInterceptor> interceptor;
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
