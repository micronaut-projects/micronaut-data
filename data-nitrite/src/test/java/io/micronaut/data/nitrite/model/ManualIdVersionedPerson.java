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

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Version;
import java.util.UUID;

/**
 * Entity with a manually assigned (not {@code @GeneratedValue}) UUID id, combined with a
 * defaulted {@code @Version} field. Used to reproduce a version-initialization regression
 * that only surfaces when the id is non-null before the first save.
 */
@MappedEntity("manual_id_versioned_persons")
public class ManualIdVersionedPerson {

    @Id
    private UUID id = UUID.randomUUID();

    private String name;

    private int age;

    @Version
    private Long version = 0L;

    public ManualIdVersionedPerson() {}

    public ManualIdVersionedPerson(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
