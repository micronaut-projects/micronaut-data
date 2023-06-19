/*
 * Copyright 2017-2023 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.connection.async.AsyncConnectionOperations;
import io.micronaut.data.connection.reactive.ReactiveStreamsConnectionOperations;

/**
 * The registry of various connection operations managers.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
public interface ConnectionOperationsRegistry {

    /**
     * Provide synchronous connection operations.
     *
     * @param connectionManagerType The operations type
     * @param dataSourceName        The datasource name
     * @param <T>                   The operations type
     * @return the provided instance
     */
    @NonNull
    <T extends ConnectionOperations<?>> T provideSynchronous(@NonNull Class<T> connectionManagerType, @Nullable String dataSourceName);

    /**
     * Provide reactive connection operations.
     *
     * @param connectionManagerType The operations type
     * @param dataSourceName        The datasource name
     * @param <T>                   The operations type
     * @return the provided instance
     */
    @NonNull
    <T extends ReactiveStreamsConnectionOperations<?>> T provideReactive(@NonNull Class<T> connectionManagerType, @Nullable String dataSourceName);

    /**
     * Provide async connection operations.
     *
     * @param connectionManagerType The operations type
     * @param dataSourceName        The datasource name
     * @param <T>                   The operations type
     * @return the provided instance
     */
    @NonNull
    <T extends AsyncConnectionOperations<?>> T provideAsync(@NonNull Class<T> connectionManagerType, @Nullable String dataSourceName);

}
