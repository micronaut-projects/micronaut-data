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
package io.micronaut.data.hibernate.reactive.entities;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
public class Pet {

    @Id
    @GeneratedValue
    private UUID id;
    private String name;
    @Enumerated(EnumType.STRING)
    private Pet.PetType type = Pet.PetType.DOG;

    public void setName(String name) {
        this.name = name;
    }


    public String getName() {
        return name;
    }

    public UUID getId() {
        return id;
    }

    public PetType getType() {
        return type;
    }

    public void setType(PetType type) {
        this.type = type;
    }

    public void setId(UUID id) {
        this.id = id;
    }


    public enum PetType {
        DOG,
        CAT
    }
}

