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
package io.micronaut.data.connection.interceptor;

import io.micronaut.core.annotation.Nullable;

/**
 * Resolves the tenant data source name for the connection manager.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@FunctionalInterface
public interface ConnectionDataSourceTenantResolver {


    /**
     * Resolves the tenant's data source name.
     * @return The data source name
     */
    @Nullable
    String resolveTenantDataSourceName();
}
