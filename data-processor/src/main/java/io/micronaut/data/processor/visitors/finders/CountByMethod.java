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
import io.micronaut.data.intercept.CountInterceptor;
import io.micronaut.data.intercept.DataInterceptor;
import io.micronaut.data.intercept.async.CountAsyncInterceptor;
import io.micronaut.data.intercept.reactive.CountReactiveInterceptor;
import io.micronaut.data.model.query.ProjectionList;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.processor.visitors.MatchContext;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;

import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Dynamic finder for support for counting.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class CountByMethod extends DynamicFinder {

    /**
     * Default constructor.
     */
    public CountByMethod() {
        super("count");
    }

    @Nullable
    @Override
    protected MethodMatchInfo buildInfo(@NonNull MethodMatchContext matchContext, @NonNull ClassElement queryResultType, @Nullable QueryModel query) {
        return buildCountInfo(matchContext, query);
    }

    @Override
    public boolean isMethodMatch(MethodElement methodElement, MatchContext matchContext) {
        return super.isMethodMatch(methodElement, matchContext) &&
                isValidCountReturnType(methodElement, matchContext);
    }

    /**
     * Builds count info.
     * @param matchContext The match context
     * @param query The query
     * @return The method info
     */
    MethodMatchInfo buildCountInfo(@NonNull MethodMatchContext matchContext, @Nullable QueryModel query) {
        Class<? extends DataInterceptor> interceptor = CountInterceptor.class;
        ClassElement returnType = matchContext.getReturnType();
        if (TypeUtils.isFutureType(returnType)) {
            interceptor = CountAsyncInterceptor.class;
            returnType = returnType.getGenericType().getFirstTypeArgument().orElse(returnType);
        } else if (TypeUtils.isReactiveType(returnType)) {
            interceptor = CountReactiveInterceptor.class;
            returnType = returnType.getGenericType().getFirstTypeArgument().orElse(returnType);
        }
        if (query != null) {
            ProjectionList projections = query.projections();
            projections.count();
            return new MethodMatchInfo(
                    returnType,
                    query,
                    getInterceptorElement(matchContext, interceptor)
            );
        } else {
            return new MethodMatchInfo(
                    returnType,
                    matchContext.supportsImplicitQueries() ? null : QueryModel.from(matchContext.getRootEntity()),
                    getInterceptorElement(matchContext, interceptor)
            );
        }
    }

    /**
     * Checks whether the return type is supported.
     * @param methodElement The method element
     * @param matchContext The match context
     * @return True if it is supported
     */
    static boolean isValidCountReturnType(MethodElement methodElement, MatchContext matchContext) {
        return TypeUtils.doesReturnNumber(methodElement) ||
                (TypeUtils.isReactiveOrFuture(matchContext.getReturnType()) &&
                        TypeUtils.isNumber(matchContext.getReturnType().getFirstTypeArgument().orElse(null)));
    }
}
