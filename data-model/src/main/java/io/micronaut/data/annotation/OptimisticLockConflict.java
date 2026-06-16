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
package io.micronaut.data.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines the optimistic lock conflict handling policy for a repository method.
 *
 * @author radovanradic
 * @since 5.1
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
public @interface OptimisticLockConflict {

    /**
    * @return The conflict handling policy
    */
    Policy value() default Policy.FAIL_FAST;

    /**
     * @return The number of reload-and-retry attempts for {@link Policy#RELOAD_AND_RETRY}.
     */
    int maxRetries() default 1;

    /**
     * Optimistic lock conflict handling policies.
     */
    enum Policy {
        /**
         * Rethrow the {@link io.micronaut.data.exceptions.OptimisticLockException}.
         */
        FAIL_FAST,
        /**
         * Delegate the conflict handling to {@link io.micronaut.data.exceptions.OptimisticLockExceptionHandler}.
         */
        DELEGATE,
        /**
         * Reload the latest entity state, merge the requested update by reusing method arguments,
         * and retry the method invocation.
         */
        RELOAD_AND_RETRY
    }
}
