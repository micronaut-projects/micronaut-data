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

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest
@SQLiteDBProperties
class SQLiteMappedEntityTest {

    @Inject
    SQLiteDoubleImplement1Repository di1;

    @Inject
    SQLiteDoubleImplement2Repository di2;

    @Inject
    SQLiteDoubleImplement3Repository di3;

    @Test
    void testMappedEntitiesWithMultipleInterfaces() {
        assertNotNull(di1.get());
        assertNotNull(di2.get());
        assertNotNull(di3.get());
    }
}
