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
package io.micronaut.data.jdbc.oraclexe

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.OracleContainer

class Oracle {
    static OracleContainer oracleContainer
    static void init() {
         if (oracleContainer == null) {
             oracleContainer = new OracleContainer("registry.gitlab.com/micronaut-projects/micronaut-graal-tests/oracle-database:18.4.0-xe")
             oracleContainer.start()
         }
    }

    static String getJdbcUrl() {
        if (oracleContainer == null) {
            init()
        }
        oracleContainer.getJdbcUrl()
    }

    static String getUsername() {
        if (oracleContainer == null) {
            init()
        }
        oracleContainer.getUsername()
    }

    static String getPassword() {
        if (oracleContainer == null) {
            init()
        }
        oracleContainer.getPassword()
    }

    static void destroy(GenericContainer container) {
        if (container != null) {
            container.stop()
            container = null
        }
    }
}
