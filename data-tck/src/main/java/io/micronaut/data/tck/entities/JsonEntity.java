package io.micronaut.data.tck.entities;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;

@MappedEntity
public class JsonEntity {

    @Id
    private Long id;

    @TypeDef(type = DataType.JSON)
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
