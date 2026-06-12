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
import io.micronaut.data.annotation.Index;
import io.micronaut.data.annotation.MappedEntity;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

@MappedEntity
@Index(columns = "email", unique = true)
public record CustomerProfile(
    @Id
    @GeneratedValue
    @Nullable
    Long id,

    @NotBlank
    String email,

    @NotBlank
    String displayName) {

    public CustomerProfile(String email, String displayName) {
        this(null, email, displayName);
    }
}
