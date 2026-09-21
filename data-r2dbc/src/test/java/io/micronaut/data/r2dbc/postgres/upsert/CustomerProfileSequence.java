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
package io.micronaut.data.r2dbc.postgres.upsert;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Index;
import io.micronaut.data.annotation.MappedEntity;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

@MappedEntity
@Index(columns = "email", unique = true)
public class CustomerProfileSequence {

    @Id
    @GeneratedValue(value = GeneratedValue.Type.SEQUENCE)
    @Nullable
    private Long id;

    @NotBlank
    private String email;

    @NotBlank
    private String displayName;

    public CustomerProfileSequence() {
    }

    public CustomerProfileSequence(String email, String displayName) {
        this(null, email, displayName);
    }

    public CustomerProfileSequence(@Nullable Long id, String email, String displayName) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
    }

    @Nullable
    public Long getId() {
        return id;
    }

    public void setId(@Nullable Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
