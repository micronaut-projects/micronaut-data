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
package io.micronaut.data.jdbc.runtime;

import io.micronaut.core.annotation.NonNull;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * SQL PreparedStatement callback interface that helps with dealing with SQLException.
 *
 * @param <R> The return type
 *
 * @author graemerocher
 * @since 1.0.0
 */
public interface PreparedStatementCallback<R> {

    /**
     * Subclasses should implement this method to allow generic exception handling.
     *
     * @param statement The statement
     * @return The result
     * @throws SQLException If an error occurs
     */
    @NonNull
    R call(@NonNull PreparedStatement statement) throws SQLException;
}
