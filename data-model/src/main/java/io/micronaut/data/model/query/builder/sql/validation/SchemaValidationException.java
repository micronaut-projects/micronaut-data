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
package io.micronaut.data.model.query.builder.sql.validation;

import io.micronaut.core.annotation.Internal;

/**
 * A schema validation exception thrown if mapped entities don't have matching tables and columns in the database.
 *
 * @author radovanradic
 * @since 4.13.0
 */
@Internal
public class SchemaValidationException extends RuntimeException {

    /**
     * @param message The message
     */
    public SchemaValidationException(String message) {
        super(message);
    }

    /**
     * @param message The message
     * @param cause The cause
     */
    public SchemaValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
