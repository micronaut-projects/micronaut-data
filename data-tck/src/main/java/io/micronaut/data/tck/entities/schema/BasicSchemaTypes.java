package io.micronaut.data.tck.entities.schema;

import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.tck.entities.BasicTypes;

import java.net.MalformedURLException;

/**
 * The entity used for schema creation and validation.
 */
@MappedEntity("basic_schema_types")
public final class BasicSchemaTypes extends BasicTypes {

    public BasicSchemaTypes() throws MalformedURLException {
    }
}
