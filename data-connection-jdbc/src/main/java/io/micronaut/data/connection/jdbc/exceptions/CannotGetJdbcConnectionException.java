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
package io.micronaut.data.connection.jdbc.exceptions;

/**
 * Exception thrown when a JDBC connection cannot be retrieved.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
public final class CannotGetJdbcConnectionException extends RuntimeException {

    /**
     * Default constructor.
     * @param message The message
     */
    public CannotGetJdbcConnectionException(String message) {
        super(message);
    }

    /**
     * Default constructor.
     * @param message The message
     * @param cause The cause
     */
    public CannotGetJdbcConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
