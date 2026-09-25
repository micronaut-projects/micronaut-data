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
package io.micronaut.data.tck.entities.schema;

import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.tck.entities.BasicTypes;

import java.net.MalformedURLException;

/**
 * The entity used for schema creation and validation.
 */
@MappedEntity("basic_types_schema_test")
public final class BasicTypesSchemaTest extends BasicTypes {

    /**
     * Constructs a new instance of BasicTypesSchemaTest.
     *
     * @throws MalformedURLException if the URL in the superclass is malformed
     */
    public BasicTypesSchemaTest() throws MalformedURLException {
        super();
    }
}
