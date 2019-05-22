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
package io.micronaut.data.processor.visitors.finders.page;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.intercept.FindPageInterceptor;
import io.micronaut.data.processor.visitors.MatchContext;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.data.processor.visitors.finders.MethodMatchInfo;
import io.micronaut.data.processor.visitors.finders.QueryListMethod;
import io.micronaut.data.processor.visitors.finders.RawQuery;
import io.micronaut.inject.ast.ClassElement;

/**
 * hands a query result of type {@link io.micronaut.data.model.Page}.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class QueryPageMethod extends QueryListMethod {

    @Override
    public int getOrder() {
        return super.getOrder() - 10;
    }

    @Override
    protected boolean isValidReturnType(@NonNull ClassElement returnType, MatchContext matchContext) {
        return matchContext.isTypeInRole(returnType, TypeRole.PAGE);
    }

    @Override
    protected MethodMatchInfo buildMatchInfo(@NonNull MethodMatchContext matchContext, @NonNull RawQuery query) {
        if (!matchContext.hasParameterInRole(TypeRole.PAGEABLE)) {
            matchContext.fail("Method must accept an argument that is a Pageable");
            return null;
        }
        return new MethodMatchInfo(
                matchContext.getReturnType().getFirstTypeArgument().orElse(matchContext.getRootEntity().getType()),
                query,
                FindPageInterceptor.class
        );
    }
}
