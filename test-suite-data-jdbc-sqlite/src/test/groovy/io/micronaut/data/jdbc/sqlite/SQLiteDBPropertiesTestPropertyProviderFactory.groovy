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
package io.micronaut.data.jdbc.sqlite

import io.micronaut.test.support.TestPropertyProvider
import io.micronaut.test.support.TestPropertyProviderFactory

import java.nio.file.Files

class SQLiteDBPropertiesTestPropertyProviderFactory implements TestPropertyProviderFactory {

    @Override
    TestPropertyProvider create(Map<String, Object> availableProperties, Class<?> testClass) {
        SQLiteDBProperties sqliteDbProperties = testClass.getAnnotation(SQLiteDBProperties)
        if (sqliteDbProperties == null) {
            return Collections::emptyMap
        }
        return () -> [
            'datasources.default.name'           : sqliteDbProperties.name(),
            'datasources.default.packages'       : sqliteDbProperties.packages(),
            'datasources.default.schema-generate': sqliteDbProperties.schemaGenerate(),
            'datasources.default.dialect'        : sqliteDbProperties.dialect(),
            'datasources.default.db-type'        : sqliteDbProperties.dbType(),
            'datasources.default.driverClassName': sqliteDbProperties.driverClassName(),
            'datasources.default.url'            : sqliteDbProperties.url() ? sqliteDbProperties.url() : createUrl(sqliteDbProperties.name()),
            'datasources.default.username'       : sqliteDbProperties.username(),
            'datasources.default.password'       : sqliteDbProperties.password()
        ] as Map<String, String>
    }

    private static String createUrl(String name) {
        def databaseFile = Files.createTempFile(name.toLowerCase(Locale.ENGLISH), ".db").toFile()
        databaseFile.deleteOnExit()
        return "jdbc:sqlite:${databaseFile.absolutePath}"
    }
}
