package io.micronaut.data.document.mongodb.upsert.model;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import org.bson.types.ObjectId;

@MappedEntity("songs2")
public class SongEntity2 {

    @Id
    ObjectId id;
    String name;

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
