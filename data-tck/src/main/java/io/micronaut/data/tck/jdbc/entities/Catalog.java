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
package io.micronaut.data.tck.jdbc.entities;

import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;

import io.micronaut.core.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

@MappedEntity
public class Catalog {
    @Id
    @AutoPopulated
    private UUID id;

    @NotBlank
    private String name;

    @Nullable
    @MappedProperty
    @Relation(value = Relation.Kind.ONE_TO_ONE)
    private Catalog parent;


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    @Nullable
    public Catalog getParent() {
        return parent;
    }

    public void setParent(@Nullable Catalog parent) {
        this.parent = parent;
    }
}

