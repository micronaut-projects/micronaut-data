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
package io.micronaut.data.jdbc.sqlite;

import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.model.DataType;
import jakarta.persistence.Column;

import java.util.UUID;

@MappedEntity("uuid_entity")
public class SqliteUuidEntity {

    @AutoPopulated
    @Id
    private UUID uuid;

    private String name;

    @Column(nullable = true)
    @MappedProperty(type = DataType.UUID)
    private UUID nullableValue;

    public SqliteUuidEntity() {
    }

    public SqliteUuidEntity(String name) {
        this.name = name;
    }

    public SqliteUuidEntity(String name, UUID nullableValue) {
        this.name = name;
        this.nullableValue = nullableValue;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getNullableValue() {
        return nullableValue;
    }

    public void setNullableValue(UUID nullableValue) {
        this.nullableValue = nullableValue;
    }
}
