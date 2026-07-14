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
package io.micronaut.data.runtime.config;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlDialectOptions;
import org.jspecify.annotations.Nullable;

/**
 * Common SQL dialect option configuration shared by JDBC and R2DBC datasource configuration.
 *
 * @since 5.1
 */
@Internal
public class SqlDialectOptionsConfiguration {

    @Nullable
    private String version;

    /**
     * @return The target dialect version.
     */
    @Nullable
    public String getVersion() {
        return version;
    }

    /**
     * @param version The target dialect version.
     */
    public void setVersion(@Nullable String version) {
        this.version = version;
    }

    /**
     * Resolve the configured options for a dialect.
     *
     * @param dialect The dialect
     * @return The resolved options
     */
    public SqlDialectOptions toDialectOptions(Dialect dialect) {
        return SqlDialectOptions.of(dialect, version);
    }
}
