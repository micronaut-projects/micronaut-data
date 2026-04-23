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
package io.micronaut.data.jdbc.sqlite;

import io.micronaut.test.support.TestPropertyProvider;
import io.micronaut.test.support.TestPropertyProviderFactory;

import java.util.Collections;
import java.util.Map;

public class SQLiteDBPropertiesTestPropertyProviderFactory implements TestPropertyProviderFactory {

    @Override
    public TestPropertyProvider create(Map<String, Object> availableProperties, Class<?> testClass) {
        SQLiteDBProperties sqliteDbProperties = testClass.getAnnotation(SQLiteDBProperties.class);
        JavaSQLiteDBProperties javaSqliteDbProperties = testClass.getAnnotation(JavaSQLiteDBProperties.class);
        if (sqliteDbProperties == null && javaSqliteDbProperties == null) {
            return Collections::emptyMap;
        }
        return () -> Map.of(
            "datasources.default.name", sqliteDbProperties != null ? sqliteDbProperties.name() : javaSqliteDbProperties.name(),
            "datasources.default.packages", sqliteDbProperties != null ? sqliteDbProperties.packages() : javaSqliteDbProperties.packages(),
            "datasources.default.schema-generate", sqliteDbProperties != null ? sqliteDbProperties.schemaGenerate() : javaSqliteDbProperties.schemaGenerate(),
            "datasources.default.dialect", sqliteDbProperties != null ? sqliteDbProperties.dialect() : javaSqliteDbProperties.dialect(),
            "datasources.default.db-type", sqliteDbProperties != null ? sqliteDbProperties.dbType() : javaSqliteDbProperties.dbType(),
            "datasources.default.driverClassName", sqliteDbProperties != null ? sqliteDbProperties.driverClassName() : javaSqliteDbProperties.driverClassName(),
            "datasources.default.url", sqliteDbProperties != null ? sqliteDbProperties.url() : javaSqliteDbProperties.url(),
            "datasources.default.username", sqliteDbProperties != null ? sqliteDbProperties.username() : javaSqliteDbProperties.username(),
            "datasources.default.password", sqliteDbProperties != null ? sqliteDbProperties.password() : javaSqliteDbProperties.password()
        );
    }
}
