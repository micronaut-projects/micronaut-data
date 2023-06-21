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
package io.micronaut.data.runtime.multitenancy;

/**
 * The multi-tenancy mode.
 *
 * @author Denis Stepanov
 * @since 3.9.0
 */
public enum MultiTenancyMode {
    /**
     * A separate database with a separate connection pool is used to store each tenants data.
     */
    DATASOURCE,
    /**
     * The same database, but different schemas are used to store each tenants data.
     */
    SCHEMA,
    /**
     * The same database is used with a discriminator used to partition and isolate data.
     */
    DISCRIMINATOR
}
