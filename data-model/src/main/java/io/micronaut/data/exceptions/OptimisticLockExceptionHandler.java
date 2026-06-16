/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.exceptions;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.Nullable;

/**
 * Handles optimistic lock conflicts for repository method invocations.
 *
 * @author radovanradic
 * @since 5.1
 */
@Experimental
public interface OptimisticLockExceptionHandler {

    /**
     * Handles the optimistic lock conflict.
     *
     * @param exception The optimistic lock exception
     * @param context   The invocation context
     * @return The fallback method result
     */
    @Nullable
    Object handle(OptimisticLockException exception, MethodInvocationContext<?, ?> context);
}
