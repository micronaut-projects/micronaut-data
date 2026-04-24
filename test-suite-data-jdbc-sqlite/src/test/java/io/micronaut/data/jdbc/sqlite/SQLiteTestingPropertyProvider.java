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
package io.micronaut.data.jdbc.sqlite;

import io.micronaut.data.runtime.config.SchemaGenerate;
import io.micronaut.test.support.TestPropertyProvider;

import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public interface SQLiteTestingPropertyProvider extends TestPropertyProvider {

    default SchemaGenerate schemaGenerate() {
        return SchemaGenerate.CREATE;
    }

    default List<String> packages() {
        String currentClassPackage = getClass().getPackage().getName();
        return Arrays.asList(currentClassPackage, "io.micronaut.data.tck.entities", "io.micronaut.data.tck.jdbc.entities");
    }

    default boolean shouldAddDefaultDbProperties() {
        return true;
    }

    @Override
    default Map<String, String> getProperties() {
        return shouldAddDefaultDbProperties() ? getSQLiteDataSourceProperties("default") : Map.of();
    }

    default Map<String, String> getSQLiteDataSourceProperties(String dataSourceName) {
        String prefix = "datasources." + dataSourceName;
        String url = createUrl(dataSourceName);
        return Map.of(
            (prefix + ".url"), url,
            (prefix + ".schema-generate"), schemaGenerate().toString(),
            (prefix + ".dialect"), "SQLITE",
            (prefix + ".db-type"), "sqlite",
            (prefix + ".username"), "",
            (prefix + ".password"), "",
            (prefix + ".packages"), packages().stream().reduce("", (a, b) -> a + "," + b),
            (prefix + ".driverClassName"), "org.sqlite.JDBC"
        );
    }

    private static String createUrl(String dataSourceName) {
        try {
            var databaseFile = Files.createTempFile(dataSourceName.toLowerCase(Locale.ENGLISH), ".db").toFile();
            databaseFile.deleteOnExit();
            return "jdbc:sqlite:" + databaseFile.getAbsolutePath();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create SQLite test database", e);
        }
    }

}
