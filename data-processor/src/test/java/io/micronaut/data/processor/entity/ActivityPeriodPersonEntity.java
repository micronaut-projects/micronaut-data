package io.micronaut.data.processor.entity;

import io.micronaut.data.annotation.EmbeddedId;
import io.micronaut.data.annotation.MappedEntity;

@MappedEntity("activity_period_person")
public class ActivityPeriodPersonEntity {

    @EmbeddedId
    private ActivityPeriodPersonId id;

    public ActivityPeriodPersonId getId() {
        return id;
    }

    public void setId(ActivityPeriodPersonId id) {
        this.id = id;
    }
}
