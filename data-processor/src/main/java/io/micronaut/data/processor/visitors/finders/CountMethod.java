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

/**
 * Simple count method.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class CountMethod extends AbstractListMethod {

    /**
     * Default constructor.
     */
    public CountMethod() {
        super("count");
    }

    @Override
    public int getOrder() {
        // lower priority than dynamic finder
        return DEFAULT_POSITION + 100;
    }

    @Override
    public boolean isMethodMatch(MethodElement methodElement, MatchContext matchContext) {
        return super.isMethodMatch(methodElement, matchContext) && CountByMethod.isValidCountReturnType(methodElement, matchContext);
    }

    @Nullable
    @Override
    protected MethodMatchInfo buildInfo(@NonNull MethodMatchContext matchContext, @NonNull ClassElement queryResultType, @Nullable QueryModel query) {
        return buildCountInfo(
                matchContext, query
        );
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

}
