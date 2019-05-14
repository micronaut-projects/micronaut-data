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
import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.intercept.FindByIdInterceptor;
import io.micronaut.data.intercept.FindOneInterceptor;
import io.micronaut.data.model.query.Query;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.visitors.MatchContext;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;

/**
 * Simple list method support.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class FindOneMethod extends AbstractListMethod {

    /**
     * The prefixes used.
     */
    public static final String[] PREFIXES = {"get", "find", "search", "query"};

    public FindOneMethod() {
        super(PREFIXES);
    }

    @Override
    public int getOrder() {
        // lower priority than dynamic finder
        return DEFAULT_POSITION + 100;
    }

    @Override
    public boolean isMethodMatch(@NonNull MethodElement methodElement, @NonNull MatchContext matchContext) {
        return super.isMethodMatch(methodElement, matchContext) && matchContext.getReturnType().hasStereotype(Introspected.class);
    }

    @Nullable
    @Override
    public MethodMatchInfo buildMatchInfo(@NonNull MethodMatchContext matchContext) {
        SourcePersistentEntity rootEntity = matchContext.getRootEntity();
        if (!rootEntity.getName().equals(matchContext.getReturnType().getName())) {
            matchContext.fail("Simple find method must return an entity of type: " + rootEntity.getName());
            return null;
        } else {
            return super.buildMatchInfo(matchContext);
        }
    }

    @Nullable
    @Override
    protected MethodMatchInfo buildInfo(MethodMatchContext matchContext, @NonNull ClassElement queryResultType, @Nullable Query query) {
        if (query != null) {
            List<Query.Criterion> criterionList = query.getCriteria().getCriteria();
            if (criterionList.size() == 1 && criterionList.get(0) instanceof Query.IdEquals) {
                return new MethodMatchInfo(
                        matchContext.getReturnType(),
                        query,
                        FindByIdInterceptor.class
                );

            }
        }
        return new MethodMatchInfo(
            matchContext.getReturnType(),
            query,
            FindOneInterceptor.class
        );
    }
}
