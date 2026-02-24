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
package io.micronaut.data.jdbc.h2;

import io.micronaut.data.runtime.config.SchemaGenerate;
import io.micronaut.test.support.TestPropertyProvider;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public interface H2TestingPropertyProvider extends TestPropertyProvider {

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
        return shouldAddDefaultDbProperties() ? getH2DataSourceProperties("default") : Map.of();
    }

    default Map<String, String> getH2DataSourceProperties(String dataSourceName) {
        String prefix = "datasources." + dataSourceName;
        return Map.of(
            (prefix + ".url"), "jdbc:h2:mem:" + dataSourceName + ";LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE",
            (prefix + ".schema-generate"), schemaGenerate().toString(),
            (prefix + ".dialect"), "h2",
            (prefix + ".username"), "",
            (prefix + ".password"), "",
            (prefix + ".packages"), packages().stream().reduce("", (a, b) -> a + "," + b),
            (prefix + ".driverClassName"), "org.h2.Driver"
        );
    }

}
