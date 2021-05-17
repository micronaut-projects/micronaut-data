package io.micronaut.data.jdbc.mysql;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.jdbc.annotation.ColumnTransformer;
import io.micronaut.data.model.DataType;

import javax.persistence.Column;
import java.util.UUID;

@MappedEntity
public class MySqlUuidEntity {

    @Id
    @Column(columnDefinition = "binary(16)")
    @ColumnTransformer(read = "BIN_TO_UUID(@.id)", write = "UUID_TO_BIN(?)")
    private UUID id;


    @Column(columnDefinition = "binary(16)")
    @ColumnTransformer(read = "BIN_TO_UUID(@.id2)", write = "UUID_TO_BIN(?)")
    private UUID id2;

    @TypeDef(type = DataType.BYTE_ARRAY)
    @Column(columnDefinition = "binary(16)")
    private UUID id3;

    @Column
    private String name;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getId2() {
        return id2;
    }

    public void setId2(UUID id2) {
        this.id2 = id2;
    }

    public UUID getId3() {
        return id3;
    }

    public void setId3(UUID id3) {
        this.id3 = id3;
    }
}
