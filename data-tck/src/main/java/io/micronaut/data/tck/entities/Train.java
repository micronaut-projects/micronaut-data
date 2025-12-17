/*
 * Copyright 2017-2025 original authors
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

import org.jspecify.annotations.Nullable;
import io.micronaut.data.annotation.GenerateJakartaDataMetamodel;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.Embedded;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import java.time.LocalDateTime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

@GenerateJakartaDataMetamodel
@Entity
public class Train {
    @Id
    @GeneratedValue
    private Long id;

    private String name;
    @Nullable
    @io.micronaut.core.annotation.Nullable
    private String model;
    private int capacity;
    private double speed;
    private boolean electric;
    @Nullable
    @io.micronaut.core.annotation.Nullable
    private LocalDateTime departureTime;
    @Nullable
    @io.micronaut.core.annotation.Nullable
    private Instant createdAt;
    @Nullable
    @io.micronaut.core.annotation.Nullable
    private LocalDate departureDate;
    @Nullable
    @io.micronaut.core.annotation.Nullable
    private LocalTime departureTimeOnly;

    @Embedded
    private TrainSpecs specs;

    @ManyToOne(cascade = CascadeType.ALL)
    private TrainManufacturer manufacturer;

    public Train() {
    }

    public Train(String name, @Nullable @io.micronaut.core.annotation.Nullable String model, int capacity, double speed, boolean electric) {
        this.name = name;
        this.model = model;
        this.capacity = capacity;
        this.speed = speed;
        this.electric = electric;
    }

    public Train(String name, @Nullable @io.micronaut.core.annotation.Nullable String model, int capacity, double speed, boolean electric, LocalDateTime departureTime, Instant createdAt) {
        this.name = name;
        this.model = model;
        this.capacity = capacity;
        this.speed = speed;
        this.electric = electric;
        this.departureTime = departureTime;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public boolean isElectric() {
        return electric;
    }

    public void setElectric(boolean electric) {
        this.electric = electric;
    }

    public LocalDateTime getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(LocalDateTime departureTime) {
        this.departureTime = departureTime;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDate getDepartureDate() {
        return departureDate;
    }

    public void setDepartureDate(LocalDate departureDate) {
        this.departureDate = departureDate;
    }

    public LocalTime getDepartureTimeOnly() {
        return departureTimeOnly;
    }

    public void setDepartureTimeOnly(LocalTime departureTimeOnly) {
        this.departureTimeOnly = departureTimeOnly;
    }

    public TrainSpecs getSpecs() {
        return specs;
    }

    public void setSpecs(TrainSpecs specs) {
        this.specs = specs;
    }

    public TrainManufacturer getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(TrainManufacturer manufacturer) {
        this.manufacturer = manufacturer;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Train train = (Train) o;
        return Objects.equals(id, train.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
