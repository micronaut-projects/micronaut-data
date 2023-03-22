/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.tck.entities;

import io.micronaut.core.annotation.Creator;
import io.micronaut.data.annotation.*;

import io.micronaut.core.annotation.Nullable;
import jakarta.persistence.Id;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.Date;
import java.util.Set;

@MappedEntity
@Where("@.actual = 'Y'")
public class Meal {

    @Id
    @GeneratedValue
    private Long mid;

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
    private Set<Food> foods = Collections.emptySet();

    private char actual = 'Y';

    public Meal(@NotNull @Max(999) int currentBloodGlucose, Date createdOn, Date updatedOn) {
        this.currentBloodGlucose = currentBloodGlucose;
        this.createdOn = createdOn;
        this.updatedOn = updatedOn;
        this.actual = 'Y';
    }

    @Creator
    public Meal(
            Long mid,
            @NotNull @Max(999) int currentBloodGlucose,
            Date createdOn,
            Date updatedOn,
            @Nullable Set<Food> foods) {
        this.mid = mid;
        this.currentBloodGlucose = currentBloodGlucose;
        this.createdOn = createdOn;
        this.updatedOn = updatedOn;
        this.foods = foods;
        this.actual = 'Y';
    }

    public Meal(@NotNull @Max(9999) int currentBloodGlucose) {
        this.currentBloodGlucose = currentBloodGlucose;
    }

    public Long getMid() {
        return mid;
    }

    public void setMid(Long mid) {
        this.mid = mid;
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

    public char isActual() {
        return actual;
    }

    public void setActual(char actual) {
        this.actual = actual;
    }
}
