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
package io.micronaut.data.jdbc.notification;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.exceptions.ExceptionHandler;

/**
 * Handles change-listener failures after configured retry and recovery advice completes.
 *
 * <p>Register a bean implementing this interface to replace the default logging handler.
 * The handler runs synchronously on the listener worker and can be called concurrently for
 * different listeners. Returning does not retry or redeliver the failed event. Failures from
 * an asynchronous listener body are handled by its async executor.</p>
 *
 * @since 5.3.0
 */
@Experimental
@FunctionalInterface
public interface ChangeListenerExceptionHandler extends ExceptionHandler<ChangeListenerException> {
}
