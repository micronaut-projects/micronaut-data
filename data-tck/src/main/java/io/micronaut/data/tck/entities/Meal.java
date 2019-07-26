package io.micronaut.data.tck.entities;

import io.micronaut.core.annotation.Creator;
import io.micronaut.data.annotation.*;

import javax.annotation.Nullable;
import javax.persistence.Id;
import javax.validation.constraints.Max;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@MappedEntity
public class Meal {

    @Id
    @AutoPopulated
    private UUID id;

    @NotNull
    @Max(999)
    private int currentBloodGlucose;

    @DateCreated
    private Date createdOn;

    @DateUpdated
    private Date updatedOn;

    @Relation(
            value = Relation.Kind.ONE_TO_MANY,
            mappedBy = "meal")
    private Set<Food> foods = new HashSet<>();

    public Meal(@NotNull @Max(999) int currentBloodGlucose, Date createdOn, Date updatedOn) {
        this.currentBloodGlucose = currentBloodGlucose;
        this.createdOn = createdOn;
        this.updatedOn = updatedOn;
    }

    @Creator
    public Meal(
            UUID id,
            @NotNull @Max(999) int currentBloodGlucose,
            Date createdOn,
            Date updatedOn,
            @Nullable Set<Food> foods) {
        this.id = id;
        this.currentBloodGlucose = currentBloodGlucose;
        this.createdOn = createdOn;
        this.updatedOn = updatedOn;
        this.foods = foods;
    }

    public Meal(@NotNull @Size(max = 999) int currentBloodGlucose) {
        this.currentBloodGlucose = currentBloodGlucose;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public int getCurrentBloodGlucose() {
        return currentBloodGlucose;
    }

    public void setCurrentBloodGlucose(int currentBloodGlucose) {
        this.currentBloodGlucose = currentBloodGlucose;
    }

    public Date getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(Date createdOn) {
        this.createdOn = createdOn;
    }

    public Date getUpdatedOn() {
        return updatedOn;
    }

    public void setUpdatedOn(Date updatedOn) {
        this.updatedOn = updatedOn;
    }

    public Set<Food> getFoods() {
        return foods;
    }

    public void setFoods(Set<Food> foods) {
        this.foods = foods;
    }

}