/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.data.connection.support;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.order.Ordered;
import io.micronaut.data.connection.ConnectionStatus;

import java.util.function.Function;

/**
 * Customizes connection before or after data repository call based on the provided {@link ConnectionStatus}.
 *
 * Implementations of this interface can modify the behavior of connections created by Micronaut Data
 * or do what might be needed before or after call to the data repository (for example JDBC statement call).
 *
 * @see ConnectionStatus
 * @param <C> The connection type
 *
 * @author radovanradic
 * @since 4.11
 */
@Experimental
public interface ConnectionCustomizer<C> extends Ordered {

    /**
     * Intercept the connection operation.
     * @param operation The operation
     * @param <R> The result
     * @return the operation callback
     */
    <R> Function<ConnectionStatus<C>, R> intercept(Function<ConnectionStatus<C>, R> operation);
}
