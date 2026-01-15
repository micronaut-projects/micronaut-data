/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.data.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Hint to drivers to use the given fetch size for streaming repository methods.
 * - For JDBC: applied to methods returning {@code java.util.stream.Stream<T>}, it will call {@code PreparedStatement#setFetchSize(int)}.
 * - For R2DBC: applied to methods returning {@code reactor.core.publisher.Flux<T>}, it will call {@code io.r2dbc.spi.Statement#fetchSize(int)}.
 * - For Hibernate: applied to methods returning {@code java.util.stream.Stream<T>}.
 *
 * Implementations may ignore the hint if unsupported by the underlying driver.
 *
 * @author radovanradic
 * @since 5.0
 */
@Documented
@Retention(RUNTIME)
@Target(METHOD)
public @interface Fetch {

    /**
     * Will be used in streaming operations if method is not {@link Fetch} annotated.
     */
    int DEFAULT_FETCH_SIZE = 1000;

    /**
     * The desired fetch size to use for streaming operations.
     * @return the fetch size (must be > 0 to take effect)
     */
    int value();
}
