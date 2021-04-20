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
import io.micronaut.core.order.Ordered;
import io.micronaut.data.processor.visitors.MatchContext;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.ast.MethodElement;

/**
 * Implementation of dynamic finders.
 *
 * @author graeme rocher
 * @since 1.0
 */
public interface MethodCandidate extends Ordered {

    /**
     * The default position.
     */
    int DEFAULT_POSITION = 0;

    /**
     * Whether the given method name matches this finder.
     *
     * @param methodElement The method element. Never null.
     * @param matchContext The match context. Never null.
     * @return true if it does
     */
    boolean isMethodMatch(@NonNull MethodElement methodElement, @NonNull MatchContext matchContext);

    @Override
    default int getOrder() {
        return DEFAULT_POSITION;
    }

    /**
     * Builds the method info. The method {@link #isMethodMatch(MethodElement, MatchContext)} should be
     * invoked and checked prior to calling this method.
     *
     * @param matchContext The match context
     * @return The method info or null if it cannot be built. If the method info cannot be built an error will be reported to
     * the passed {@link MethodMatchContext}
     */
    @Nullable
    MethodMatchInfo buildMatchInfo(@NonNull MethodMatchContext matchContext);

}
