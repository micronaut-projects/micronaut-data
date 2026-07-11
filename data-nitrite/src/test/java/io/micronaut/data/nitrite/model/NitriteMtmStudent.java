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

import java.util.ArrayList;
import java.util.List;

@MappedEntity("nitrite_mtm_student")
public class NitriteMtmStudent {
    @Id
    @GeneratedValue
    private String id;
    private String name;

    @Relation(value = Relation.Kind.MANY_TO_MANY, cascade = Relation.Cascade.ALL)
    private List<NitriteMtmCourse> courses = new ArrayList<>();

    public NitriteMtmStudent() {
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

    public List<NitriteMtmCourse> getCourses() {
        return courses;
    }

    public void setCourses(List<NitriteMtmCourse> courses) {
        this.courses = courses;
    }
}
