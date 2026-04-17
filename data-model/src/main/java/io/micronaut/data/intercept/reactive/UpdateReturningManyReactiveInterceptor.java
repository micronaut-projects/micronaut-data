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
package io.micronaut.data.intercept.reactive;

import io.micronaut.data.intercept.DataInterceptor;

/**
 * A {@link DataInterceptor} that updates multiple records and returns the updated results reactively.
 *
 * @param <T> The declaring type
 * @param <R> The result type
 * @author radovanradic
 * @since 5.0.0
 */
public interface UpdateReturningManyReactiveInterceptor<T, R> extends DataInterceptor<T, R> {
}
