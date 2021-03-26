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
import io.micronaut.data.intercept.DataInterceptor;
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
import java.util.Map;
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
                .filter(p -> !p.hasAnnotation(Id.class))
                .collect(Collectors.toList());

        ParameterElement idParameter = parameters.stream().filter(p -> p.hasAnnotation(Id.class)).findFirst()
                .orElse(null);
        if (idParameter == null) {
            matchContext.fail("ID required for update method, but not specified");
            return null;
        }

        SourcePersistentEntity entity = matchContext.getRootEntity();
        // Validate @IdClass for composite entity
        if (entity.hasIdentity()) {
            SourcePersistentProperty identity = entity.getIdentity();
            String idType = TypeUtils.getTypeName(identity.getType());
            String idParameterType = TypeUtils.getTypeName(idParameter.getType());
            if (!idType.equals(idParameterType)) {
                matchContext.fail("ID type of method [" + idParameterType + "] does not match ID type of entity: " + idType);
            }
        } else {
            matchContext.fail("Cannot update by ID for entity that has no ID");
        }

        QueryModel query = QueryModel.from(entity);
        query.idEq(new QueryParameter(idParameter.getName()));
        List<String> properiesToUpdate = new ArrayList<>(remainingParameters.size());
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

        Element element = matchContext.getParametersInRole().get(TypeRole.LAST_UPDATED_PROPERTY);
        if (element instanceof PropertyElement) {
            properiesToUpdate.add(element.getName());
        }

        ClassElement returnType = matchContext.getReturnType();
        Map.Entry<ClassElement, Class<? extends DataInterceptor>> entry = FindersUtils.pickUpdateInterceptor(matchContext, returnType);
        MethodMatchInfo info = new MethodMatchInfo(
                entry.getKey(),
                query,
                getInterceptorElement(matchContext, entry.getValue()),
                MethodMatchInfo.OperationType.UPDATE,
                properiesToUpdate.toArray(new String[0])
        );

        info.addParameterRole(
                TypeRole.ID,
                idParameter.getName()
        );
        return info;
    }

}
