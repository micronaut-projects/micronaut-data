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

import org.jspecify.annotations.Nullable;

/**
 * Common SQL dialect option configuration shared by JDBC and R2DBC datasource configuration.
 *
 * @since 5.1
 */
public class SqlDialectOptionsConfiguration {

    @Nullable
    private String version;

    /**
     * @return The target dialect version.
     * @since 5.1
     */
    @Nullable
    public String getVersion() {
        return version;
    }

    /**
     * @param version The target dialect version.
     * @since 5.1
     */
    public void setVersion(@Nullable String version) {
        this.version = version;
    }

}
