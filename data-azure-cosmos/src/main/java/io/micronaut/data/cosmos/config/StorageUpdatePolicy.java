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
package io.micronaut.data.cosmos.config;

/**
 * This enum is used to tell how we should handle database and containers during startup.
 *
 * @author radovanradic
 * @since 4.0.0
 */
public enum StorageUpdatePolicy {

    /**
     * Database (or container) will be created if not exists with given properties from the configuration.
     */
    CREATE_IF_NOT_EXISTS,

    /**
     * Database (or container) will be updated with the properties from the configuration.
     */
    UPDATE,

    /**
     * No attempt to create or update database (or container) will be made.
     */
    NONE
}
