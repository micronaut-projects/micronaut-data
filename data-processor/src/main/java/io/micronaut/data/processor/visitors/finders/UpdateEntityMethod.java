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

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.intercept.DataInterceptor;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.query.QueryParameter;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import io.micronaut.data.processor.visitors.MatchContext;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Handles {@link io.micronaut.data.repository.CrudRepository#update(Object)}.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class UpdateEntityMethod extends AbstractPatternBasedMethod implements MethodCandidate {

    public static final Pattern METHOD_PATTERN = Pattern.compile("^((update)(\\S*?))$");

    /**
     * The default constructor.
     */
    public UpdateEntityMethod() {
        super(METHOD_PATTERN);
    }

    @NonNull
    @Override
    protected MethodMatchInfo.OperationType getOperationType() {
        return MethodMatchInfo.OperationType.UPDATE;
    }

    @Override
    public int getOrder() {
        return DEFAULT_POSITION - 100;
    }

    @Override
    public boolean isMethodMatch(MethodElement methodElement, MatchContext matchContext) {
        ParameterElement[] parameters = matchContext.getParameters();
        return parameters.length > 0 && hasMatchingParameters(Arrays.stream(parameters)) && super.isMethodMatch(methodElement, matchContext);
    }

    private boolean hasMatchingParameters(Stream<ParameterElement> parameters) {
        return parameters.anyMatch(p -> TypeUtils.isIterableOfEntity(p.getGenericType()) || p.getGenericType().hasAnnotation(MappedEntity.class));
    }

    @Nullable
    @Override
    public MethodMatchInfo buildMatchInfo(@NonNull MethodMatchContext matchContext) {
        List<ParameterElement> parameters = matchContext.getParametersNotInRole();
        if (CollectionUtils.isNotEmpty(parameters)) {
            if (hasMatchingParameters(parameters.stream())) {
                ParameterElement matchingParameter = null;
                boolean isEntityParameter = false;
                boolean isMultipleEntityParameter = false;
                if (parameters.size() > 0) {
                    ParameterElement parameterElement = parameters.get(0);
                    if (TypeUtils.isIterableOfEntity(parameterElement.getGenericType())) {
                        matchingParameter = parameterElement;
                        isMultipleEntityParameter = true;
                    }
                    if (parameterElement.getGenericType().hasAnnotation(MappedEntity.class)) {
                        matchingParameter = parameterElement;
                        isEntityParameter = true;
                    }
                }
                if (matchingParameter == null) {
                    matchContext.failAndThrow("Cannot find a parameter representing the entity update");
                    return null;
                }

                Map.Entry<ClassElement, Class<? extends DataInterceptor>> matchEntry = FindersUtils.resolveInterceptorTypeByOperationType(
                        isEntityParameter,
                        isMultipleEntityParameter,
                        MethodMatchInfo.OperationType.UPDATE,
                        matchContext);

                ClassElement returnType = matchEntry.getKey();
                if (returnType == null) {
                    returnType = matchContext.getRootEntity().getType();
                } else if (!TypeUtils.isVoid(returnType)
                        && !TypeUtils.isNumber(returnType)
                        && !returnType.hasStereotype(MappedEntity.class)) {
                    matchContext.failAndThrow("Cannot implement update method for specified return type: " + returnType.getName());
                    return null;
                }

                Class<? extends DataInterceptor> interceptor = matchEntry.getValue();
                QueryModel queryModel = null;
                String[] updateProperties = null;
                if (!matchContext.supportsImplicitQueries()) {
                    final SourcePersistentEntity rootEntity = matchContext.getRootEntity();
                    final String idName;
                    final SourcePersistentProperty identity = rootEntity.getIdentity();
                    if (identity != null) {
                        idName = identity.getName();
                    } else {
                        idName = TypeRole.ID;
                    }
                    queryModel = QueryModel.from(rootEntity)
                            .idEq(new QueryParameter(idName));
                    if (rootEntity.getVersion() != null) {
                        queryModel = queryModel.versionEq(new QueryParameter(rootEntity.getVersion().getName()));
                    }
                    updateProperties = Stream.concat(rootEntity.getPersistentProperties().stream(), Stream.of(rootEntity.getVersion()))
                            .filter(p -> p != null && !((p instanceof Association) && ((Association) p).isForeignKey()) && !p.isGenerated() &&
                                    p.findAnnotation(AutoPopulated.class).map(ap -> ap.getRequiredValue(AutoPopulated.UPDATEABLE, Boolean.class)).orElse(true))
                            .map(PersistentProperty::getName)
                            .toArray(String[]::new);
                    if (ArrayUtils.isEmpty(updateProperties)) {
                        queryModel = null;
                    }
                }

                MethodMatchInfo methodMatchInfo = new MethodMatchInfo(
                        returnType,
                        queryModel,
                        getInterceptorElement(matchContext, interceptor),
                        MethodMatchInfo.OperationType.UPDATE,
                        updateProperties
                );
                if (isEntityParameter) {
                    methodMatchInfo.addParameterRole(TypeRole.ENTITY, matchingParameter.getName());
                }
                if (isMultipleEntityParameter) {
                    methodMatchInfo.addParameterRole(TypeRole.ENTITIES, matchingParameter.getName());
                }
                if (queryModel != null && matchContext.getRootEntity().getVersion() != null) {
                    methodMatchInfo.setOptimisticLock(true);
                }
                return methodMatchInfo;
            }
        }
        matchContext.fail("Cannot implement update method for specified arguments and return type");
        return null;
    }

}

