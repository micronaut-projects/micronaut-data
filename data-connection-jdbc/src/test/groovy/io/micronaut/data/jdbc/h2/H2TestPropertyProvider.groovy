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

import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.test.support.TestPropertyProvider

trait H2TestPropertyProvider implements TestPropertyProvider {

    SchemaGenerate schemaGenerate() {
        return SchemaGenerate.CREATE
    }

    List<String> packages() {
        def currentClassPackage = getClass().package.name
        return Arrays.asList(currentClassPackage, "io.micronaut.data.tck.entities", "io.micronaut.data.tck.jdbc.entities")
    }

    Map<String, String> getProperties() {
        return getH2DataSourceProperties("default")
    }

    Map<String, String> getH2DataSourceProperties(String dataSourceName) {
        def prefix = 'datasources.' + dataSourceName
        return [
                (prefix + '.url')            : "jdbc:h2:mem:${dataSourceName};LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE",
                (prefix + '.schema-generate'): schemaGenerate(),
                (prefix + '.dialect')        : 'h2',
                (prefix + '.username')       : '',
                (prefix + '.password')       : '',
                (prefix + '.packages')       : packages(),
                (prefix + '.driverClassName'): "org.h2.Driver"
        ] as Map<String, String>
    }

}
