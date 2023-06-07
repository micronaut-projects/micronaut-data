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
package io.micronaut.transaction.exceptions;


import java.time.Duration;

/**
 * Exception that gets thrown when an invalid timeout is specified,
 * that is, the specified timeout valid is out of range or the
 * transaction manager implementation doesn't support timeouts.
 *
 * @author Juergen Hoeller
 * @author graemerocher
 * @since 12.05.2003
 */
public class InvalidTimeoutException extends TransactionUsageException {

    private final Duration timeout;

    /**
     * Constructor for InvalidTimeoutException.
     * @param msg the detail message
     * @param timeout the invalid timeout value
     */
    public InvalidTimeoutException(String msg, Duration timeout) {
        super(msg);
        this.timeout = timeout;
    }

    /**
     * @return Return the invalid timeout value.
     */
    public Duration getTimeout() {
        return this.timeout;
    }

}

