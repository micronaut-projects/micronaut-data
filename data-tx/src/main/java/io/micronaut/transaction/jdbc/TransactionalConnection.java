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
package io.micronaut.transaction.jdbc;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.core.annotation.Internal;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Allows injecting a {@link Connection} instance as a bean with any methods invoked
 * on the connection being delegated to connection bound to the current transaction.
 *
 * <p>If no transaction is </p>
 * @author graemerocher
 * @since 1.0
 */
@EachBean(DataSource.class)
@TransactionalConnectionAdvice
@Internal
public interface TransactionalConnection extends Connection {

}
