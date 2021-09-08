/*
 * Copyright 2017-2021 original authors
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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.order.Ordered;
import io.micronaut.data.processor.visitors.MethodMatchContext;

/**
 * The method matcher.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Experimental
public interface MethodMatcher extends Ordered {

    /**
     * The default position.
     */
    int DEFAULT_POSITION = 0;

    @Override
    default int getOrder() {
        return DEFAULT_POSITION;
    }

    @Nullable
    MethodMatch match(@NonNull MethodMatchContext matchContext);

    /**
     * Method match implementation.
     */
    interface MethodMatch {

        /**
         * Match info builder.
         *
         * @param matchContext The match context
         * @return The match info
         */
        MethodMatchInfo buildMatchInfo(MethodMatchContext matchContext);

    }
}
