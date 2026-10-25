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
package io.micronaut.data.neo4j.session;

import org.neo4j.driver.Driver;

/**
 * A Neo4j driver that graph repositories can use, by the name repositories refer to it with:
 * {@code @Neo4jRepository("name")}. The driver of {@code micronaut-neo4j} is named {@code default}, and each driver
 * configured under {@code micronaut.data.neo4j.drivers.<name>} is named after its key.
 *
 * @param name   The name of the driver
 * @param driver The driver
 * @author graemerocher
 * @since 5.3.0
 */
public record Neo4jDriverRegistration(String name, Driver driver) implements AutoCloseable {

    /**
     * Closes the driver. The default driver belongs to {@code micronaut-neo4j}, which closes it.
     */
    @Override
    public void close() {
        if (!"default".equals(name)) {
            driver.close();
        }
    }
}
