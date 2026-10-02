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
package io.micronaut.data.tck.jdbc.entities.upsert;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Index;
import io.micronaut.data.annotation.MappedEntity;
import jakarta.persistence.Embedded;
import org.jspecify.annotations.Nullable;

@MappedEntity
@Index(name = "uk_embedded_conflict_key", columns = {"region", "code"}, unique = true)
public class EmbeddedConflictEntity {

    @Id
    @GeneratedValue
    @Nullable
    private Integer id;

    @Embedded
    private EmbeddedConflictKey key;

    private String name;

    public EmbeddedConflictEntity() {
    }

    public EmbeddedConflictEntity(EmbeddedConflictKey key, String name) {
        this.key = key;
        this.name = name;
    }

    @Nullable
    public Integer getId() {
        return id;
    }

    public void setId(@Nullable Integer id) {
        this.id = id;
    }

    public EmbeddedConflictKey getKey() {
        return key;
    }

    public void setKey(EmbeddedConflictKey key) {
        this.key = key;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
