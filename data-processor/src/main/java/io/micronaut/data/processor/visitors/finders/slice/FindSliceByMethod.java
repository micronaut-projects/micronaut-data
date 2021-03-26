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
package io.micronaut.data.processor.visitors.finders.slice;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.processor.visitors.MatchContext;
import io.micronaut.data.processor.visitors.finders.FindByFinder;
import io.micronaut.inject.ast.MethodElement;

/**
 * Handles dynamic finders that return a {@link io.micronaut.data.model.Slice}.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class FindSliceByMethod extends FindByFinder {

    public static final int POSITION = DEFAULT_POSITION - 150;

    @Override
    public int getOrder() {
        return POSITION;
    }

    @Override
    protected boolean isCompatibleReturnType(@NonNull MethodElement methodElement, @NonNull MatchContext matchContext) {
        return matchContext.isTypeInRole(
                matchContext.getReturnType(),
                TypeRole.SLICE
        );
    }
}
