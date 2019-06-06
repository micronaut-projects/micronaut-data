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

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.processor.visitors.MatchContext;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;

/**
 * Dynamic finder for support for delete operations.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class DeleteByMethod extends DynamicFinder {

    protected static final String[] PREFIXES = {"delete", "remove", "erase", "eliminate"};

    /**
     * Default constructor.
     */
    public DeleteByMethod() {
        super(PREFIXES);
    }

    @Override
    public boolean isMethodMatch(MethodElement methodElement, MatchContext matchContext) {
        return super.isMethodMatch(methodElement, matchContext) && TypeUtils.isValidBatchUpdateReturnType(methodElement); // void return
    }

    @Nullable
    @Override
    protected MethodMatchInfo buildInfo(
            MethodMatchContext matchContext, ClassElement queryResultType, @Nullable QueryModel query) {
        if (query == null) {
            matchContext.fail("Unable to implement delete method with no query arguments");
            return null;
        } else {
            return new MethodMatchInfo(
                    null,
                    query,
                    DeleteMethod.pickDeleteAllInterceptor(matchContext.getReturnType()),
                    MethodMatchInfo.OperationType.DELETE
            );
        }
    }
}
