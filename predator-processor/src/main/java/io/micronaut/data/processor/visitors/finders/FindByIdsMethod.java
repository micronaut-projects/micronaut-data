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
package io.micronaut.data.processor.visitors.finders;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.data.intercept.FindAllInterceptor;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.query.QueryParameter;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import io.micronaut.data.processor.visitors.MatchContext;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;

import java.util.regex.Pattern;

/**
 * Supports a method called findByIds.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class FindByIdsMethod extends AbstractPatternBasedMethod {

    /**
     * Default constructor.
     */
    public FindByIdsMethod() {
        super(Pattern.compile("^(find|search|get|list)ByIds$"));
    }

    @Override
    public int getOrder() {
        return FindByFinder.DEFAULT_POSITION - 200;
    }

    @Override
    public boolean isMethodMatch(@NonNull MethodElement methodElement, @NonNull MatchContext matchContext) {
        return super.isMethodMatch(methodElement, matchContext)
                && TypeUtils.isIterableOfEntity(matchContext.getReturnType()) &&
                areParametersValid(matchContext);
    }

    @Nullable
    @Override
    public MethodMatchInfo buildMatchInfo(@NonNull MethodMatchContext matchContext) {
        SourcePersistentEntity rootEntity = matchContext.getRootEntity();
        SourcePersistentProperty identity = rootEntity.getIdentity();
        if (identity != null) {

            QueryModel query = QueryModel.from(rootEntity);
            query.inList(identity.getName(), new QueryParameter(matchContext.getParameters()[0].getName()));

            return new MethodMatchInfo(
                    matchContext.getReturnType(),
                    query,
                    FindAllInterceptor.class);
        } else {
            matchContext.fail("Cannot query by ID on entity that defines no ID");
            return null;
        }
    }

    private boolean areParametersValid(@NonNull MatchContext matchContext) {
        ParameterElement[] parameters = matchContext.getParameters();
        return parameters.length == 1 && (parameters[0].isArray() || (parameters[0].getType() != null && parameters[0].getType().isAssignable(Iterable.class)));
    }
}
