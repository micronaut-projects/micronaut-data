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

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.sql.JoinColumn;
import io.micronaut.data.annotation.sql.JoinColumns;

import java.util.Objects;

/**
 * Mirrors a Kotlin entity that exposes its association as a mutable property but takes it as a
 * non-nullable constructor parameter: the property could be populated after instantiation, yet the
 * constructor still rejects a null association, so the mapper cannot defer it.
 */
@MappedEntity
public class CompositeFkRequiredChild {

    @Id
    @GeneratedValue
    private String id;

    private String name;

    @Relation(Relation.Kind.MANY_TO_ONE)
    @JoinColumns({
        @JoinColumn(name = "fk_tenant_id", referencedColumnName = "tenantId"),
        @JoinColumn(name = "fk_ref_id", referencedColumnName = "refId")
    })
    private CompositeFkParent parent;

    @Creator
    public CompositeFkRequiredChild(@NonNull String name, @NonNull CompositeFkParent parent) {
        this.name = Objects.requireNonNull(name, "name");
        this.parent = Objects.requireNonNull(parent, "parent");
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public CompositeFkParent getParent() {
        return parent;
    }

    public void setParent(CompositeFkParent parent) {
        this.parent = parent;
    }
}
