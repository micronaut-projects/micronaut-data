/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.data.jdbc.sqlite

import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.test.support.TestPropertyProvider

import java.nio.file.Files

trait SqliteTestPropertyProvider implements TestPropertyProvider {

    SchemaGenerate schemaGenerate() {
        return SchemaGenerate.CREATE
    }

    List<String> packages() {
        return [getClass().package.name]
    }

    @Override
    Map<String, String> getProperties() {
        def databaseFile = Files.createTempFile(getClass().simpleName.toLowerCase(Locale.ENGLISH), ".db").toFile()
        databaseFile.deleteOnExit()
        String prefix = 'datasources.default'
        return [
            (prefix + '.url')            : "jdbc:sqlite:${databaseFile.absolutePath}",
            (prefix + '.schema-generate'): schemaGenerate(),
            (prefix + '.dialect')        : 'SQLITE',
            (prefix + '.packages')       : packages(),
            (prefix + '.driverClassName'): 'org.sqlite.JDBC',
            (prefix + '.username')       : '',
            (prefix + '.password')       : ''
        ] as Map<String, String>
    }
}
