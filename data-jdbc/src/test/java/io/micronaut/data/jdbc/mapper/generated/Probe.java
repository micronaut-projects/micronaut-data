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
package io.micronaut.data.jdbc.mapper.generated;
import io.micronaut.core.annotation.Creator;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

/**
 * An entity whose constructor is private and that the introspection builds through a static creator with fewer
 * parameters than the constructor.
 */
@MappedEntity("generated_probe")
public class Probe {

    @Id
    @GeneratedValue
    private Long id;
    private String name;
    private boolean active;

    private Probe(Long id, String name, boolean active) {
        this.id = id;
        this.name = name;
        this.active = active;
    }

    @Creator
    public static Probe of(Long id, String name) {
        return new Probe(id, name, true);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
