package io.micronaut.data.tck.entities;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.JsonRepresentation;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.JsonDataType;

@MappedEntity
public class JsonEntity {

    @Id
    private Long id;

    @TypeDef(type = DataType.JSON)
    @JsonRepresentation(type = JsonDataType.DEFAULT)
    @Nullable
    private SampleData jsonDefault;

    @TypeDef(type = DataType.JSON)
    @JsonRepresentation(type = JsonDataType.BLOB)
    @Nullable
    private SampleData jsonBlob;

    @TypeDef(type = DataType.JSON)
    @JsonRepresentation(type = JsonDataType.STRING)
    @Nullable
    private SampleData jsonString;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @Nullable
    public SampleData getJsonDefault() {
        return jsonDefault;
    }

    public void setJsonDefault(@Nullable SampleData jsonDefault) {
        this.jsonDefault = jsonDefault;
    }

    @Nullable
    public SampleData getJsonBlob() {
        return jsonBlob;
    }

    public void setJsonBlob(@Nullable SampleData jsonBlob) {
        this.jsonBlob = jsonBlob;
    }

    @Nullable
    public SampleData getJsonString() {
        return jsonString;
    }

    public void setJsonString(@Nullable SampleData jsonString) {
        this.jsonString = jsonString;
    }
}
