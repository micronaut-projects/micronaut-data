package io.micronaut.data.r2dbc.mysql;

import io.micronaut.data.annotation.DataTransformer;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

import javax.persistence.Column;
import java.util.UUID;

@MappedEntity
public class MySqlUuidEntity {

    @Id
    @Column(columnDefinition = "binary(16)")
    @DataTransformer(read = "BIN_TO_UUID(@.id)", write = "UUID_TO_BIN(?)")
    private UUID id;


    @Column(columnDefinition = "binary(16)")
    @DataTransformer(read = "BIN_TO_UUID(@.id2)", write = "UUID_TO_BIN(?)")
    private UUID id2;

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
}
