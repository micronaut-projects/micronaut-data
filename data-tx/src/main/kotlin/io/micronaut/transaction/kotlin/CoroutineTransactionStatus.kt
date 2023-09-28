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
package io.micronaut.transaction.kotlin

import io.micronaut.core.annotation.Experimental
import io.micronaut.data.connection.ConnectionStatus
import io.micronaut.transaction.TransactionExecution

/**
 * Status object for coroutine transactions.
 *
 * @param <T> The connection type.
 * @author Denis Stepanov
 * @since 4.2.0
 */
@Experimental
interface CoroutineTransactionStatus<T> : TransactionExecution {

    /**
     * @return The connection.
     */
    val connection: T

    /**
     * @return The connection status.
     */
    val connectionStatus: ConnectionStatus<T>
}
