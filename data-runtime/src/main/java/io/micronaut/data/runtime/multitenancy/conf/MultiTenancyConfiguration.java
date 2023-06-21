/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.runtime.multitenancy.conf;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.runtime.config.DataSettings;
import io.micronaut.data.runtime.multitenancy.MultiTenancyMode;

/**
 * The multi-tenancy configuration.
 *
 * @author Denis Stepanov
 * @since 3.8.0
 */
@ConfigurationProperties(MultiTenancyConfiguration.PREFIX)
public final class MultiTenancyConfiguration {

    /**
     * Prefix for config.
     */
    static final String PREFIX = DataSettings.PREFIX + ".multi-tenancy";

    /**
     * The multi-tenancy mode.
     */
    private MultiTenancyMode mode;

    /**
     * Multi-tenancy mode specified.
     *
     * @return The multi tenancy mode set or a null if not set
     */
    @Nullable
    public MultiTenancyMode getMode() {
        return mode;
    }

    /**
     * Sets the multi-tenancy mode.
     * @param mode The multi-tenancy mode
     */
    public void setMode(MultiTenancyMode mode) {
        this.mode = mode;
    }
}
