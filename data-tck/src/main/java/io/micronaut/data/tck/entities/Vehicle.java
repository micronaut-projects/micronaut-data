/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Index;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import jakarta.persistence.Embedded;

@MappedEntity
public class Vehicle {

    @GeneratedValue
    @Id
    private Long id;

    @Index(columns = "name")
    private String name;

    @Embedded
    private Registration firstRegistration;

    @Embedded
    @MappedProperty("second_")
    private Registration secondRegistration;

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

    public Registration getFirstRegistration() {
        return firstRegistration;
    }

    public void setFirstRegistration(Registration firstRegistration) {
        this.firstRegistration = firstRegistration;
    }

    public Registration getSecondRegistration() {
        return secondRegistration;
    }

    public void setSecondRegistration(Registration secondRegistration) {
        this.secondRegistration = secondRegistration;
    }
}
