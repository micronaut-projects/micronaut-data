/*
 * Copyright 2017-2019 original authors
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
import io.micronaut.data.intercept.DeleteAllInterceptor;
import io.micronaut.data.intercept.DeleteOneInterceptor;
import io.micronaut.data.intercept.PredatorInterceptor;
import io.micronaut.data.intercept.async.DeleteAllAsyncInterceptor;
import io.micronaut.data.intercept.async.DeleteOneAsyncInterceptor;
import io.micronaut.data.intercept.reactive.DeleteAllReactiveInterceptor;
import io.micronaut.data.intercept.reactive.DeleteOneReactiveInterceptor;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.processor.visitors.MatchContext;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Support for simple delete operations.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class DeleteMethod extends AbstractListMethod {

    /**
     * Default constructor.
     */
    public DeleteMethod() {
        super("delete", "remove", "erase", "eliminate");
    }

    @Override
    public final int getOrder() {
        // lower priority than dynamic finder
        return DEFAULT_POSITION + 100;
    }

    @Override
    public boolean isMethodMatch(MethodElement methodElement, MatchContext matchContext) {
        return super.isMethodMatch(methodElement, matchContext) &&
                TypeUtils.isValidBatchUpdateReturnType(methodElement);
    }

    @Nullable
    @Override
    public MethodMatchInfo buildMatchInfo(@NonNull MethodMatchContext matchContext) {
        ParameterElement[] parameters = matchContext.getParameters();
        Class<? extends PredatorInterceptor> interceptor = null;
        if (parameters.length == 1) {
            ClassElement genericType = parameters[0].getGenericType();
            if (genericType.isAssignable(matchContext.getRootEntity().getName())) {
                ClassElement returnType = matchContext.getReturnType();
                if (TypeUtils.isFutureType(returnType)) {
                    interceptor = DeleteOneAsyncInterceptor.class;
                } else if (TypeUtils.isReactiveType(returnType)) {
                    interceptor = DeleteOneReactiveInterceptor.class;
                } else {
                    interceptor = DeleteOneInterceptor.class;
                }
            } else if (TypeUtils.isIterableOfEntity(genericType)) {
                interceptor = pickDeleteAllInterceptor(matchContext.getReturnType());
            }
            if (interceptor != null) {
                return new MethodMatchInfo(
                        null,
                        null,
                        interceptor,
                        MethodMatchInfo.OperationType.DELETE
                );
            }
        }
        return super.buildMatchInfo(matchContext);
    }

    @Nullable
    @Override
    protected MethodMatchInfo buildInfo(@NonNull MethodMatchContext matchContext, @NonNull ClassElement queryResultType, @Nullable QueryModel query) {
        Class<? extends PredatorInterceptor> interceptor = pickDeleteAllInterceptor(matchContext.getReturnType());
        if (query != null) {
            return new MethodMatchInfo(
                    null,
                    query,
                    interceptor
            );
        } else {
            return new MethodMatchInfo(
                    null,
                    null,
                    interceptor
            );
        }
    }

    /**
     * Pick the correct delete all interceptor.
     * @param returnType The return type
     * @return The interceptor
     */
    static Class<? extends PredatorInterceptor> pickDeleteAllInterceptor(ClassElement returnType) {
        Class<? extends PredatorInterceptor> interceptor;
        if (TypeUtils.isFutureType(returnType)) {
            interceptor = DeleteAllAsyncInterceptor.class;
        } else if (TypeUtils.isReactiveType(returnType)) {
            interceptor = DeleteAllReactiveInterceptor.class;
        } else {
            interceptor = DeleteAllInterceptor.class;
        }
        return interceptor;
    }

}
