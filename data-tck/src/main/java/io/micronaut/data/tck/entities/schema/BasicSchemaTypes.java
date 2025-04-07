package io.micronaut.data.tck.entities.schema;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.tck.entities.BasicTypes;

import java.net.MalformedURLException;
import java.util.UUID;

/**
 * The entity used for schema creation and validation.
 */
@MappedEntity("basic_schema_types")
public final class BasicSchemaTypes {

    @Id
    private Long myId;

    private Short shortField;

    private Integer integerField;
    private UUID uuidField;

    public Long getMyId() {
        return myId;
    }

    public void setMyId(Long myId) {
        this.myId = myId;
    }

    public Short getShortField() {
        return shortField;
    }

    public void setShortField(Short shortField) {
        this.shortField = shortField;
    }

    public Integer getIntegerField() {
        return integerField;
    }

    public void setIntegerField(Integer integerField) {
        this.integerField = integerField;
    }

    public UUID getUuidField() {
        return uuidField;
    }

    public void setUuidField(UUID uuidField) {
        this.uuidField = uuidField;
    }
}
