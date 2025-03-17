package io.micronaut.data.processor.entity;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@MappedEntity("activity_period")
public class ActivityPeriodEntity {

    @Id
    private UUID id;
    private String name;
    private String description;
    private String type;

    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "id.activityPeriod")
    private List<ActivityPeriodPersonEntity> persons = new ArrayList<>();

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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<ActivityPeriodPersonEntity> getPersons() {
        return persons;
    }

    public void setPersons(List<ActivityPeriodPersonEntity> persons) {
        this.persons = persons;
    }
}
