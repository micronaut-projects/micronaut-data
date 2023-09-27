package io.micronaut.data.processor.entity;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@MappedEntity("activity_person")
public class ActivityPersonEntity {

    @Id
    private UUID id;
    private String firstName;
    private String lastName;

    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "id.person")
    private List<ActivityPeriodPersonEntity> activityPeriods = new ArrayList<>();

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public List<ActivityPeriodPersonEntity> getActivityPeriods() {
        return activityPeriods;
    }

    public void setActivityPeriods(List<ActivityPeriodPersonEntity> activityPeriods) {
        this.activityPeriods = activityPeriods;
    }
}
