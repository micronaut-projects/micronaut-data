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
package io.micronaut.data.runtime.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parent configuration interface.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public interface DataSettings {

    /**
     * Prefix for data related config.
     */
    String PREFIX = "micronaut.data";

    /**
     * The logger that should be used to log queries.
     */
    Logger QUERY_LOG = LoggerFactory.getLogger("io.micronaut.data.query");
}
