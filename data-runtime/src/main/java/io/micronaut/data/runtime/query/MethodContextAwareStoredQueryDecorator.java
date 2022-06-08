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
package io.micronaut.data.runtime.query;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.data.model.runtime.StoredQuery;

/**
 * Special version of stored query decorator that is aware of the method invocation context.
 *
 * @author Denis Stepanov
 * @since 3.5.0
 */
@Experimental
public interface MethodContextAwareStoredQueryDecorator {

    /**
     * Decorate stored query.
     *
     * @param context     The context
     * @param storedQuery The query to be decorated
     * @param <E>         The entity type
     * @param <R>         The result type
     * @return decorated stored query
     */
    <E, R> StoredQuery<E, R> decorate(MethodInvocationContext<?, ?> context, StoredQuery<E, R> storedQuery);
}
