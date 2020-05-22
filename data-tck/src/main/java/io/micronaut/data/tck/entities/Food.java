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

import javax.annotation.Nullable;
import javax.persistence.Id;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Date;
import java.util.UUID;

@MappedEntity
public class Food {

    @Id
    @AutoPopulated
    private UUID fid;

    @Size(max=36)
    @NotNull
    private String key;

    @Size(max=9999)
    @NotNull
    private int carbohydrates;

    @Size(max=9999)
    @NotNull
    private int portionGrams;

    @DateCreated
    private Date createdOn;

    @DateUpdated
    private Date updatedOn;

    @Relation(value = Relation.Kind.MANY_TO_ONE, cascade = Relation.Cascade.ALL)
    @MappedProperty("fk_meal_id")
    private Meal meal;

    @Relation(value = Relation.Kind.MANY_TO_ONE, cascade = Relation.Cascade.ALL)
    @Nullable
    @MappedProperty("fk_alt_meal")
    private Meal alternativeMeal;

    public Food(
            @Size(max = 36) @NotNull String key,
            @Size(max = 9999) @NotNull int carbohydrates,
            @Size(max = 9999) @NotNull int portionGrams,
            @Nullable Meal meal) {
        this.key = key;
        this.carbohydrates = carbohydrates;
        this.portionGrams = portionGrams;
        this.meal = meal;
    }

    @Creator
    public Food(
            UUID fid,
            @Size(max = 36) @NotNull String key,
            @Size(max = 9999) @NotNull int carbohydrates,
            @Size(max = 9999) @NotNull int portionGrams,
            Date createdOn,
            Date updatedOn,
            @Nullable Meal meal) {
        this.fid = fid;
        this.key = key;
        this.carbohydrates = carbohydrates;
        this.portionGrams = portionGrams;
        this.createdOn = createdOn;
        this.updatedOn = updatedOn;
        this.meal = meal;
    }

    public UUID getFid() {
        return fid;
    }

    public void setFid(UUID fid) {
        this.fid = fid;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public int getCarbohydrates() {
        return carbohydrates;
    }

    public void setCarbohydrates(int carbohydrates) {
        this.carbohydrates = carbohydrates;
    }

    public int getPortionGrams() {
        return portionGrams;
    }

    public void setPortionGrams(int portionGrams) {
        this.portionGrams = portionGrams;
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

    public Meal getMeal() {
        return meal;
    }

    public void setMeal(Meal meal) {
        this.meal = meal;
    }

    @Nullable
    public Meal getAlternativeMeal() {
        return alternativeMeal;
    }

    public void setAlternativeMeal(@Nullable Meal alternativeMeal) {
        this.alternativeMeal = alternativeMeal;
    }
}
