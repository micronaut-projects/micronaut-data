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
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.intercept.DataInterceptor;
import io.micronaut.data.intercept.SaveEntityInterceptor;
import io.micronaut.data.intercept.async.SaveEntityAsyncInterceptor;
import io.micronaut.data.intercept.reactive.SaveEntityReactiveInterceptor;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.processor.visitors.MatchContext;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * A save method for saving a single entity.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class SaveEntityMethod extends AbstractPatternBasedMethod implements MethodCandidate {

    public static final Pattern METHOD_PATTERN = Pattern.compile("^((save|persist|store|insert)(\\S*?))$");

    /**
     * The default constructor.
     */
    public SaveEntityMethod() {
        super(METHOD_PATTERN);
    }

    @NonNull
    @Override
    protected MethodMatchInfo.OperationType getOperationType() {
        return MethodMatchInfo.OperationType.INSERT;
    }

    @Override
    public boolean isMethodMatch(MethodElement methodElement, MatchContext matchContext) {
        ParameterElement[] parameters = matchContext.getParameters();
        return parameters.length == 1 &&
                super.isMethodMatch(methodElement, matchContext) && isValidSaveReturnType(matchContext, false);
    }

    @Nullable
    @Override
    public MethodMatchInfo buildMatchInfo(@NonNull MethodMatchContext matchContext) {
        VisitorContext visitorContext = matchContext.getVisitorContext();
        ParameterElement[] parameters = matchContext.getParameters();
        if (ArrayUtils.isNotEmpty(parameters)) {
            if (Arrays.stream(parameters).anyMatch(p -> p.getGenericType().hasAnnotation(MappedEntity.class))) {
                ClassElement returnType = matchContext.getReturnType();
                Class<? extends DataInterceptor> interceptor = pickSaveInterceptor(returnType);
                if (TypeUtils.isReactiveOrFuture(returnType)) {
                    returnType = returnType.getGenericType().getFirstTypeArgument().orElse(returnType);
                }

                if (matchContext.supportsImplicitQueries()) {
                    return new MethodMatchInfo(returnType, null, getInterceptorElement(matchContext, interceptor), MethodMatchInfo.OperationType.INSERT);
                } else {
                    return new MethodMatchInfo(returnType,
                            QueryModel.from(matchContext.getRootEntity()),
                            getInterceptorElement(matchContext, interceptor),
                            MethodMatchInfo.OperationType.INSERT
                    );
                }
            }
        }
        visitorContext.fail(
                "Cannot implement save method for specified arguments and return type",
                matchContext.getMethodElement()
        );
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
            interceptor = SaveEntityAsyncInterceptor.class;
        } else if (TypeUtils.isReactiveOrFuture(returnType)) {
            interceptor = SaveEntityReactiveInterceptor.class;
        } else {
            interceptor = SaveEntityInterceptor.class;
        }
        return interceptor;
    }

}
