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
import io.micronaut.data.annotation.Relation;

import java.util.List;

@MappedEntity
public class OneToManyChild {
    @Id
    @GeneratedValue
    private String id;
    private String name;

    @Relation(Relation.Kind.MANY_TO_ONE)
    private OneToManyParent parent;

    /**
     * A second ONE_TO_MANY on the child, used only so that a reverse-lookup path can name a
     * target property whose relation kind is not the MANY_TO_ONE inverse the resolver expects.
     */
    @Relation(Relation.Kind.ONE_TO_MANY)
    private List<OneToManyChild> siblings;

    public OneToManyChild() {
    }

    public OneToManyChild(String name, OneToManyParent parent) {
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

    public OneToManyParent getParent() {
        return parent;
    }

    public void setParent(OneToManyParent parent) {
        this.parent = parent;
    }

    public List<OneToManyChild> getSiblings() {
        return siblings;
    }

    public void setSiblings(List<OneToManyChild> siblings) {
        this.siblings = siblings;
    }
}
