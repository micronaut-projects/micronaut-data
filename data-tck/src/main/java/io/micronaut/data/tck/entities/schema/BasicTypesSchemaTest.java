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
