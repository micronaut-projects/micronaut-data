/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.data.model.runtime.convert;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.data.model.query.builder.sql.Dialect;

/**
 * Conversion context for JDBC implementation.
 *
 * @author Denis Stepanov
 * @since 3.1
 */
public interface DialectConversionContext extends ConversionContext {

    /**
     * Provides the SQL dialect for the current operation.
     * Default implementation returns null for backward compatibility.
     *
     * @return the dialect or null if not available
     */
    @NonNull
    Dialect getDialect();

}
