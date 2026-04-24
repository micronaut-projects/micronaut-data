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
package io.micronaut.data.connection;

import io.micronaut.core.annotation.Internal;
import java.util.function.Supplier;

/**
 * Internal fallback {@link ConnectionCapabilities} implementation that assumes all capabilities are supported.
 *
 * @since 5.0.0
 */
@Internal
final class DefaultConnectionCapabilities implements ConnectionCapabilities {
    private static final String MICROSOFT_SQL_SERVER = "Microsoft SQL Server";

    @Override
    public boolean supports(ConnectionCapabilities.Capability capability, Supplier<String> databaseProductNameSupplier) {
        String dbProductName = databaseProductNameSupplier.get();
        return capability != Capability.BATCH_INSERT || !dbProductName.equalsIgnoreCase(MICROSOFT_SQL_SERVER);
    }
}
