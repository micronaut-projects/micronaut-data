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
package io.micronaut.data.jdbc.h2

import io.micronaut.data.jdbc.DatabaseTestPropertyProvider
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.runtime.config.SchemaGenerate

trait H2TestPropertyProvider implements DatabaseTestPropertyProvider {

    @Override
    String url() {
        "jdbc:h2:mem:mydb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE"
    }

    @Override
    String username() {
        ""
    }

    @Override
    String password() {
        ""
    }

    @Override
    Dialect dialect() {
        Dialect.H2
    }

    @Override
    SchemaGenerate schemaGenerate() {
        SchemaGenerate.CREATE_DROP
    }
}
