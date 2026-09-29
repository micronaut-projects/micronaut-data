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
package io.micronaut.transaction;

/**
 * Marker interface implemented by every transaction manager, whether it manages transactions for blocking code
 * ({@link SynchronousTransactionManager}) or for reactive code. It declares no methods; the operations are
 * defined by {@link TransactionOperations} and the reactive, async and synchronous sub-interfaces.
 *
 * <p>This type is derived from the Spring Framework's {@code TransactionManager} (Apache License 2.0).</p>
 *
 * @author Juergen Hoeller
 * @author graemerocher
 * @since 1.0
 * @see SynchronousTransactionManager
 */
public interface TransactionManager {

}

