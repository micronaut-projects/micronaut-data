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
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;

/**
 * Inverse side of {@link MappedIdParent}; its identity also carries a mapped name.
 */
@MappedEntity
public class MappedIdChild {

    @Id
    @GeneratedValue
    @MappedProperty("child_id")
    private String id;

    private String name;

    @Relation(Relation.Kind.MANY_TO_ONE)
    private MappedIdParent parent;

    public MappedIdChild() {
    }

    public MappedIdChild(String name, MappedIdParent parent) {
        this.name = name;
        this.parent = parent;
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

    public MappedIdParent getParent() {
        return parent;
    }

    public void setParent(MappedIdParent parent) {
        this.parent = parent;
    }
}
