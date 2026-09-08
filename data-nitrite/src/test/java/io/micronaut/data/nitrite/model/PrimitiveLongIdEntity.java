package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

/**
 * An assigned identity declared as the primitive {@code long}, which the document key holds
 * exactly as it holds a {@code Long}.
 */
@MappedEntity
public class PrimitiveLongIdEntity {

    @Id
    private long id;

    private String name;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
