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

import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.model.DataType;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.ManyToOne;
import java.util.UUID;

@MappedEntity(alias = "uidx") // UID is reserved word in Oracle DB
public class UuidEntity {
    @AutoPopulated
    @Id
    private UUID uuid;

    @ManyToOne
    @MappedProperty
    private UuidChildEntity child;

    @Embedded
    private UuidEmbeddedChildEntity embeddedChild;

    private String name;

    @Column(nullable = true)
    @MappedProperty(type = DataType.UUID)
    private UUID nullableValue;

    public UuidEntity(String name) {
        this.name = name;
    }

    public UuidEntity(String name, UUID nullableValue) {
        this.name = name;
        this.nullableValue = nullableValue;
    }

    public String getName() {
        return name;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public UuidChildEntity getChild() {
        return child;
    }

    public void setChild(UuidChildEntity child) {
        this.child = child;
    }

    public UuidEmbeddedChildEntity getEmbeddedChild() {
        return embeddedChild;
    }

    public void setEmbeddedChild(UuidEmbeddedChildEntity embeddedChild) {
        this.embeddedChild = embeddedChild;
    }

    public UUID getNullableValue() {
        return nullableValue;
    }

    public void setNullableValue(UUID nullableValue) {
        this.nullableValue = nullableValue;
    }
}
