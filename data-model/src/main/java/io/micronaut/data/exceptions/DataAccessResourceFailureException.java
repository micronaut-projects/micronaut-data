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
package io.micronaut.data.exceptions;

/**
 * Exception thrown when the underlying resource fails to connect.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class DataAccessResourceFailureException extends DataAccessException {
    /**
     * Default constructor.
     * @param message The message
     */
    public DataAccessResourceFailureException(String message) {
        super(message);
    }

    /**
     * Default constructor.
     * @param message The message
     * @param cause The cause
     */
    public DataAccessResourceFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
