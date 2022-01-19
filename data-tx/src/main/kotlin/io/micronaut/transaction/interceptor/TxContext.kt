/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.transaction.interceptor

import io.micronaut.core.annotation.Internal
import io.micronaut.transaction.support.TransactionSynchronizationManager
import kotlinx.coroutines.ThreadContextElement
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

@Internal
class TxSynchronousContext(
        private val state: TransactionSynchronizationManager.State
) : ThreadContextElement<TransactionSynchronizationManager.State>, AbstractCoroutineContextElement(Key) {

    companion object Key : CoroutineContext.Key<TxSynchronousContext>

    override fun restoreThreadContext(context: CoroutineContext, oldState: TransactionSynchronizationManager.State) {
        TransactionSynchronizationManager.restoreState(oldState)
    }

    override fun updateThreadContext(context: CoroutineContext): TransactionSynchronizationManager.State {
        val copyState = TransactionSynchronizationManager.copyState()
        TransactionSynchronizationManager.restoreState(state)
        return copyState
    }

}