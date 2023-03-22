package io.micronaut.data.tck.entities;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.JsonDataType;
import io.micronaut.data.annotation.MappedEntity;

@MappedEntity
public class JsonEntity {

    @Id
    private Long id;

    @JsonDataType
    @Nullable
    private SampleData sampleData;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @Nullable
    public SampleData getSampleData() {
        return sampleData;
    }

    public void setSampleData(@Nullable SampleData sampleData) {
        this.sampleData = sampleData;
    }
}
