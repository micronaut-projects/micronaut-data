package io.micronaut.data.processor.entity;

import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.MappedProperty;

import java.util.Objects;

@Embeddable
public class ActivityPeriodPersonId {
    @MappedProperty("activity_period_id")
    private ActivityPeriodEntity activityPeriod;

    @MappedProperty("person_id")
    private ActivityPersonEntity person;

    public ActivityPeriodEntity getActivityPeriod() {
        return activityPeriod;
    }

    public void setActivityPeriod(ActivityPeriodEntity activityPeriod) {
        this.activityPeriod = activityPeriod;
    }

    public ActivityPersonEntity getPerson() {
        return person;
    }

    public void setPerson(ActivityPersonEntity person) {
        this.person = person;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ActivityPeriodPersonId that = (ActivityPeriodPersonId) o;
        return Objects.equals(activityPeriod, that.activityPeriod) && Objects.equals(person, that.person);
    }

    @Override
    public int hashCode() {
        return Objects.hash(activityPeriod, person);
    }
}
