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
import io.micronaut.data.intercept.UpdateEntityInterceptor;
import io.micronaut.data.intercept.async.UpdateEntityAsyncInterceptor;
import io.micronaut.data.intercept.reactive.UpdateEntityReactiveInterceptor;
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
import java.util.regex.Pattern;

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
        return parameters.length > 0 &&
                Arrays.stream(parameters).anyMatch(p -> p.getGenericType().hasAnnotation(MappedEntity.class)) &&
                super.isMethodMatch(methodElement, matchContext) && isValidSaveReturnType(matchContext, false);
    }

    @Nullable
    @Override
    public MethodMatchInfo buildMatchInfo(@NonNull MethodMatchContext matchContext) {
        List<ParameterElement> parameters = matchContext.getParametersNotInRole();
        if (CollectionUtils.isNotEmpty(parameters)) {
            if (parameters.stream().anyMatch(p -> p.getGenericType().hasAnnotation(MappedEntity.class))) {
                ClassElement returnType = matchContext.getReturnType();
                Class<? extends DataInterceptor> interceptor = pickSaveInterceptor(returnType);
                if (TypeUtils.isReactiveOrFuture(returnType)) {
                    returnType = returnType.getGenericType().getFirstTypeArgument().orElse(returnType);
                }
                if (matchContext.supportsImplicitQueries()) {
                    return new MethodMatchInfo(
                            returnType,
                            null, getInterceptorElement(matchContext, interceptor),
                            MethodMatchInfo.OperationType.UPDATE
                    );
                } else {
                    final SourcePersistentEntity rootEntity = matchContext.getRootEntity();
                    final String idName;
                    final SourcePersistentProperty identity = rootEntity.getIdentity();
                    if (identity != null) {
                        idName = identity.getName();
                    } else {
                        idName = TypeRole.ID;
                    }
                    final QueryModel queryModel = QueryModel.from(rootEntity)
                            .idEq(new QueryParameter(idName));
                    String[] updateProperties = rootEntity.getPersistentProperties()
                            .stream().filter(p ->
                                    !((p instanceof Association) && ((Association) p).isForeignKey()) &&
                                            !p.isGenerated() &&
                                            p.booleanValue(AutoPopulated.class, "updateable").orElse(true)
                            )
                            .map(PersistentProperty::getName)
                            .toArray(String[]::new);
                    if (ArrayUtils.isEmpty(updateProperties)) {
                        return new MethodMatchInfo(
                                returnType,
                                null,
                                getInterceptorElement(matchContext, interceptor),
                                MethodMatchInfo.OperationType.UPDATE
                        );
                    } else {
                        return new MethodMatchInfo(
                                returnType,
                                queryModel,
                                getInterceptorElement(matchContext, interceptor),
                                MethodMatchInfo.OperationType.UPDATE,
                                updateProperties,
                                null
                        );
                    }

                }
            }
        }
        matchContext.fail("Cannot implement update method for specified arguments and return type");
        return null;
    }

    /**
     * Is the return type valid for saving an entity.
     * @param matchContext The match context
     * @param entityArgumentNotRequired  If an entity arg is not required
     * @return True if it is
     */
    static boolean isValidSaveReturnType(@NonNull MatchContext matchContext, boolean entityArgumentNotRequired) {
        ClassElement returnType = matchContext.getReturnType();
        if (TypeUtils.isReactiveOrFuture(returnType)) {
            returnType = returnType.getFirstTypeArgument().orElse(null);
        }
        return returnType != null &&
                returnType.hasAnnotation(MappedEntity.class) &&
                (entityArgumentNotRequired || returnType.getName().equals(matchContext.getParameters()[0].getGenericType().getName()));
    }

    /**
     * Pick a runtime interceptor to use based on the return type.
     * @param returnType The return type
     * @return The interceptor
     */
    private static @NonNull Class<? extends DataInterceptor> pickSaveInterceptor(@NonNull ClassElement returnType) {
        Class<? extends DataInterceptor> interceptor;
        if (TypeUtils.isFutureType(returnType)) {
            interceptor = UpdateEntityAsyncInterceptor.class;
        } else if (TypeUtils.isReactiveOrFuture(returnType)) {
            interceptor = UpdateEntityReactiveInterceptor.class;
        } else {
            interceptor = UpdateEntityInterceptor.class;
        }
        return interceptor;
    }

}

