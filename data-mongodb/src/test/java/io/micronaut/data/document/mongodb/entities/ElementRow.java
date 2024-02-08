package io.micronaut.data.document.mongodb.entities;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import org.bson.types.ObjectId;

import java.util.UUID;

@MappedEntity
public class ElementRow {

    @Id
    @GeneratedValue
    private ObjectId id;

    private String subType;
    private long eventId;

    private String rowState;

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public String getSubType() {
        return subType;
    }

    public void setSubType(String subType) {
        this.subType = subType;
    }

    public long getEventId() {
        return eventId;
    }

    public void setEventId(long eventId) {
        this.eventId = eventId;
    }

    public String getRowState() {
        return rowState;
    }

    public void setRowState(String rowState) {
        this.rowState = rowState;
    }

}
