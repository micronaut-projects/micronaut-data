package io.micronaut.data.jdbc.mysql;

import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

import jakarta.persistence.Column;
import java.util.UUID;

@MappedEntity
public class MySqlUuidEntity2 {

    @Id
    @AutoPopulated
    @CustomBinaryMySqlUUIDType
    private UUID id;

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
}
