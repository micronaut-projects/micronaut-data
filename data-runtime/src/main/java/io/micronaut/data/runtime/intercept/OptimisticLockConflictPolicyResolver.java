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
package io.micronaut.data.runtime.intercept;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.annotation.OptimisticLockConflict;
import io.micronaut.data.exceptions.OptimisticLockException;
import io.micronaut.data.exceptions.OptimisticLockExceptionHandler;
import io.micronaut.data.intercept.annotation.DataMethod;
import org.jspecify.annotations.Nullable;

/**
 * Resolves optimistic lock conflict policy for repository method invocations.
 *
 * @since 5.1
 */
@Internal
final class OptimisticLockConflictPolicyResolver {

    @Nullable
    private final OptimisticLockExceptionHandler optimisticLockExceptionHandler;

    OptimisticLockConflictPolicyResolver(@Nullable OptimisticLockExceptionHandler optimisticLockExceptionHandler) {
        this.optimisticLockExceptionHandler = optimisticLockExceptionHandler;
    }

    OptimisticLockConflict.Policy resolvePolicy(MethodInvocationContext<?, ?> context) {
        return context.enumValue(DataMethod.class,
            DataMethod.META_MEMBER_OPTIMISTIC_LOCK_CONFLICT_POLICY,
            OptimisticLockConflict.Policy.class).orElse(OptimisticLockConflict.Policy.FAIL_FAST);
    }

    @Nullable
    Object resolveDelegate(MethodInvocationContext<?, ?> context,
                           OptimisticLockException exception) {
        if (optimisticLockExceptionHandler == null) {
            throw new IllegalStateException("No OptimisticLockExceptionHandler bean is configured for DELEGATE optimistic lock conflict policy.", exception);
        }
        return optimisticLockExceptionHandler.handle(exception, context);
    }
}
