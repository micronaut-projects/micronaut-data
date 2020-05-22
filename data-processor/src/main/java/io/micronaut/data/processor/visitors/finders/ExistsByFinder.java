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
import io.micronaut.data.intercept.ExistsByInterceptor;
import io.micronaut.data.intercept.DataInterceptor;
import io.micronaut.data.intercept.async.ExistsByAsyncInterceptor;
import io.micronaut.data.intercept.reactive.ExistsByReactiveInterceptor;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.processor.visitors.MatchContext;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;

/**
 * Dynamic finder for exists queries.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class ExistsByFinder extends DynamicFinder {

    /**
     * The prefixes used.
     */
    public static final String[] PREFIXES = new String[] { "exists" };

    /**
     * Default constructor.
     */
    public ExistsByFinder() {
        super(PREFIXES);
    }

    @Override
    public boolean isMethodMatch(MethodElement methodElement, MatchContext matchContext) {
        return super.isMethodMatch(methodElement, matchContext)
                && (TypeUtils.doesReturnBoolean(methodElement) ||
                (TypeUtils.isReactiveOrFuture(matchContext.getReturnType()) && TypeUtils.isBoolean(
                        matchContext.getReturnType().getFirstTypeArgument().orElse(null)
                )));
    }

    @Nullable
    @Override
    protected MethodMatchInfo buildInfo(
            @NonNull MethodMatchContext matchContext,
            @NonNull ClassElement queryResultType,
            @Nullable QueryModel query) {
        Class<? extends DataInterceptor> interceptor = ExistsByInterceptor.class;
        ClassElement returnType = matchContext.getReturnType();
        if (TypeUtils.isFutureType(returnType)) {
            interceptor = ExistsByAsyncInterceptor.class;
            returnType = returnType.getGenericType().getFirstTypeArgument().orElse(returnType);
        } else if (TypeUtils.isReactiveType(returnType)) {
            interceptor = ExistsByReactiveInterceptor.class;
            returnType = returnType.getGenericType().getFirstTypeArgument().orElse(returnType);
        }
        if (query == null) {
            query = matchContext.supportsImplicitQueries() ? query : QueryModel.from(matchContext.getRootEntity());
        }
        if (query != null) {
            query.projections().id();
        }
        return new MethodMatchInfo(
                returnType,
                query,
                getInterceptorElement(matchContext, interceptor)
        );
    }

}
