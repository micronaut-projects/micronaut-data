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
package io.micronaut.data.model.runtime.convert;

/**
 * The driver API a SQL operation or converter is bound to.
 *
 * <p>Micronaut Data JDBC and R2DBC can be on the same classpath. Converters that produce
 * driver-specific values declare the API they target, so each module selects its own.</p>
 *
 * @since 5.3.0
 */
public enum DataAccessType {
    /**
     * Micronaut Data JDBC.
     */
    JDBC,
    /**
     * Micronaut Data R2DBC.
     */
    R2DBC
}
