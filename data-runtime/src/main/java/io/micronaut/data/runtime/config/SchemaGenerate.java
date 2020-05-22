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

/**
 * Enum describing how to handle the schema at startup. Used for schema generation in
 * testing scenarios.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public enum SchemaGenerate {
    /**
     * Create the schema if it doesn't exist.
     */
    CREATE,
    /**
     * Drop and recreate the schema.
     */
    CREATE_DROP,
    /**
     * Do nothing.
     */
    NONE
}
