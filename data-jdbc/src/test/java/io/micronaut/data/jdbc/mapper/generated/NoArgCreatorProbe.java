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
 * An entity whose only constructor is private and that the introspection builds through a parameterless static
 * creator, every property being set afterwards.
 */
@MappedEntity("generated_no_arg_creator_probe")
public class NoArgCreatorProbe {

    @Id
    @GeneratedValue
    private Long id;
    private String name;
    private boolean created;

    private NoArgCreatorProbe() {
    }

    @Creator
    public static NoArgCreatorProbe create() {
        NoArgCreatorProbe probe = new NoArgCreatorProbe();
        probe.created = true;
        return probe;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isCreated() {
        return created;
    }
}
