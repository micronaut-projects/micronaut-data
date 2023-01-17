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
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.DateUpdated;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import java.time.Instant;

@Entity
@javax.persistence.Entity
public class Face {

    @GeneratedValue
    @Id
    @javax.persistence.GeneratedValue
    @javax.persistence.Id
    private Long id;
    private String name;

    @DateCreated
    private Instant dateCreated;

    @DateUpdated
    private Instant lastUpdated;

    public Face() {
    }

    @Creator
    public Face(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @OneToOne(mappedBy = "face")
    @javax.persistence.OneToOne(mappedBy = "face")
    private Nose nose;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Nose getNose() {
        return nose;
    }

    public void setNose(Nose nose) {
        this.nose = nose;
    }

    public Instant getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(Instant dateCreated) {
        this.dateCreated = dateCreated;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Instant lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
