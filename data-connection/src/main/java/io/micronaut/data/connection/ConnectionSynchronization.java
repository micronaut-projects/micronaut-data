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
package io.micronaut.data.connection;

import io.micronaut.core.order.Ordered;

/**
 * Interface for the connection synchronization callbacks.
 *
 * @since 4.0.0
 * @author Denis Stepanov
 */
public interface ConnectionSynchronization extends Ordered {

    /**
     * Invoked after the execution is complete.
     * This callback is always invoked after the connection execution is complete.
     * In a case if the execution is reusing the connection, the closed callbacks wouldn't be called.
     */
    default void executionComplete() {
    }

    /**
     * Invoked before the connection is closed.
     */
    default void beforeClosed() {
    }

    /**
     * Invoked after the connection is closed.
     */
    default void afterClosed() {
    }

}

