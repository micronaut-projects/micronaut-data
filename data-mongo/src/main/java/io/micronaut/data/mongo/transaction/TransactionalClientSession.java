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
package io.micronaut.data.mongo.transaction;

import com.mongodb.client.ClientSession;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.core.annotation.Internal;

/**
 * Allows injecting a {@link com.mongodb.client.ClientSession} instance as a bean with any methods invoked
 * on the connection being delegated to connection bound to the current transaction.
 *
 * @author Denis Stepanov
 * @since 3.3
 */
@EachBean(MongoSynchronousTransactionManager.class)
@TransactionalClientSessionAdvice
@Internal
public interface TransactionalClientSession extends ClientSession {

}
