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
package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Index;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.nitrite.annotation.FullTextIndex;

/**
 * Entity with a unique index and a full-text index, used to regression-test concurrent inserts
 * against those index types (nitrite 4.4.1 fixed a ConcurrentModificationException / false
 * unique-constraint violation race under concurrent writes to unique/full-text indexes).
 */
@MappedEntity
public class UniqueIndexedEntity {
    @Id
    @GeneratedValue
    private Long id;

    @Index(columns = "code", unique = true)
    private String code;

    @FullTextIndex
    private String description;

    public UniqueIndexedEntity() {
    }

    public UniqueIndexedEntity(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
