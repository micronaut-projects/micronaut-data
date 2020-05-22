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
import io.micronaut.data.intercept.DataInterceptor;
import io.micronaut.data.intercept.SaveAllInterceptor;
import io.micronaut.data.intercept.async.SaveAllAsyncInterceptor;
import io.micronaut.data.intercept.reactive.SaveAllReactiveInterceptor;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.processor.visitors.MatchContext;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;

import java.util.regex.Pattern;

/**
 * A save all method for saving several entities.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class SaveAllMethod extends AbstractPatternBasedMethod {

    private static final String METHOD_PATTERN = "^((save|persist|store|insert)(\\S*?))$";

    /**
     * Default constructor.
     */
    public SaveAllMethod() {
        super(Pattern.compile(METHOD_PATTERN));
    }

    @NonNull
    @Override
    protected MethodMatchInfo.OperationType getOperationType() {
        return MethodMatchInfo.OperationType.INSERT;
    }

    @Override
    public boolean isMethodMatch(MethodElement methodElement, MatchContext matchContext) {
        ParameterElement[] parameters = methodElement.getParameters();
        if (parameters.length == 1 && super.isMethodMatch(methodElement, matchContext)) {
            ParameterElement firstParameter = parameters[0];
            ClassElement parameterType = firstParameter.getGenericType();
            if (TypeUtils.isReactiveOrFuture(parameterType)) {
                parameterType = parameterType.getFirstTypeArgument().orElse(null);
            }
            return TypeUtils.isIterableOfEntity(parameterType);
        }
        return false;
    }

    @Nullable
    @Override
    public MethodMatchInfo buildMatchInfo(@NonNull MethodMatchContext matchContext) {
        // default doesn't build a query and query construction left to runtime
        // this is fine for JPA, for SQL we need to build an insert
        Class<? extends DataInterceptor> interceptor;
        ClassElement returnType = matchContext.getReturnType();
        if (TypeUtils.isFutureType(returnType)) {
            interceptor = SaveAllAsyncInterceptor.class;
        } else if (TypeUtils.isReactiveType(returnType)) {
            interceptor = SaveAllReactiveInterceptor.class;
        } else {
            interceptor = SaveAllInterceptor.class;
        }

        if (matchContext.supportsImplicitQueries()) {
            return new MethodMatchInfo(
                    null,
                    null,
                    getInterceptorElement(matchContext, interceptor),
                    MethodMatchInfo.OperationType.INSERT
            );
        } else {
            return new MethodMatchInfo(
                    null,
                    QueryModel.from(matchContext.getRootEntity()),
                    getInterceptorElement(matchContext, interceptor),
                    MethodMatchInfo.OperationType.INSERT
            );
        }
    }
}
