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
package io.micronaut.data.jdbc.mysql

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MySQLContainer

class MySql {
    static MySQLContainer mysql

    static void init() {
        if (mysql == null) {
            mysql = new MySQLContainer<>("mysql:8.0.17")
            mysql.start()
        }
    }

    static String getJdbcUrl() {
        if (mysql == null) {
            init()
        }
        mysql.getJdbcUrl()
    }

    static String getUsername() {
        if (mysql == null) {
            init()
        }
        mysql.getUsername()
    }

    static String getPassword() {
        if (mysql == null) {
            init()
        }
        mysql.getPassword()
    }

    static void destroy(GenericContainer container) {
        if (container != null) {
            container.stop()
            container = null
        }
    }
}
