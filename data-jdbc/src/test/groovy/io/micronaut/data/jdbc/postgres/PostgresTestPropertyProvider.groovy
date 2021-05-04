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
package io.micronaut.data.jdbc.postgres


import io.micronaut.data.jdbc.SharedDatabaseContainerTestPropertyProvider
import io.micronaut.data.model.query.builder.sql.Dialect
import org.testcontainers.containers.JdbcDatabaseContainer

import java.sql.Connection

trait PostgresTestPropertyProvider implements SharedDatabaseContainerTestPropertyProvider {

    @Override
    Dialect dialect() {
        Dialect.POSTGRES
    }

    @Override
    int sharedSpecsCount() {
        return 11
    }

    @Override
    void startContainer(JdbcDatabaseContainer container) {
        container.start()
        try (Connection connection = container.createConnection("")) {
            connection.prepareCall('CREATE EXTENSION "uuid-ossp";').execute()
        }
    }
}
