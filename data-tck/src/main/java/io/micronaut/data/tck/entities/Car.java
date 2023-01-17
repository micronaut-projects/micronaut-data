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

import io.micronaut.core.annotation.Nullable;
import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "cars", schema = "ford", catalog = "ford_cat")
@javax.persistence.Entity
@javax.persistence.Table(name = "cars", schema = "ford", catalog = "ford_cat")
public class Car {
    @GeneratedValue
    @Id
    @javax.persistence.GeneratedValue
    @javax.persistence.Id
    private Long id;

    @Nullable
    private String name;

    @OneToMany(mappedBy = "car")
    @javax.persistence.OneToMany(mappedBy = "car")
    private Set<CarPart> parts = new HashSet<>();

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

    public Set<CarPart> getParts() {
        return parts;
    }

    public void setParts(Set<CarPart> parts) {
        this.parts = parts;
    }
}
