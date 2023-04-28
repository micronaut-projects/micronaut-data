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
package io.micronaut.data.connection.exceptions;

/**
 * Exception that occurs if no connection is present.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
public class NoConnectionException extends ConnectionException {

    public NoConnectionException() {
        this("Expected an existing connection, but none was found.");
    }

    /**
     * @param message The message
     */
    public NoConnectionException(String message) {
        super(message);
    }

    /**
     * @param message The message
     * @param cause The cause
     */
    public NoConnectionException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * @return The no connection exception for missing advice connection
     */
    public static NoConnectionException notFoundInAdvice() {
        return new NoConnectionException("No current connection present. " +
            "Consider declaring @" + io.micronaut.data.connection.annotation.Connection.class.getName() +
            " or @Transactional on the surrounding method");
    }
}
