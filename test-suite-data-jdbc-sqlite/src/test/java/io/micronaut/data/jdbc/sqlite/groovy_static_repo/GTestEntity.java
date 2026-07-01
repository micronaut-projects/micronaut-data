package io.micronaut.data.jdbc.sqlite.groovy_static_repo;

import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

import java.util.UUID;

@MappedEntity
class GTestEntity {

    @Id
    @AutoPopulated
    UUID id;

    String name;

    UUID getId() {
        return id;
    }

    void setId(UUID id) {
        this.id = id;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }
}
