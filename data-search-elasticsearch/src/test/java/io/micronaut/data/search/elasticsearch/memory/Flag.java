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
package io.micronaut.data.search.elasticsearch.memory;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.model.vector.DoubleVector;
import org.jspecify.annotations.Nullable;

/**
 * An entity shaped like the Jakarta Data TCK ones: field access, and a boolean property whose
 * name starts with {@code is} while its getter is {@code isActive()}, which a JSON mapper would
 * serialize as {@code active}.
 */
@MappedEntity("flags")
@Introspected(accessKind = Introspected.AccessKind.FIELD)
public class Flag {

    @Id
    @Nullable
    String id;

    String label;

    boolean isActive;

    @Relation(Relation.Kind.EMBEDDED)
    @Nullable
    Detail detail;

    @jakarta.persistence.Column(length = 2)
    @Nullable
    DoubleVector weights;

    public @Nullable Detail getDetail() {
        return detail;
    }

    public void setDetail(@Nullable Detail detail) {
        this.detail = detail;
    }

    public @Nullable DoubleVector getWeights() {
        return weights;
    }

    public void setWeights(@Nullable DoubleVector weights) {
        this.weights = weights;
    }

    public @Nullable String getId() {
        return id;
    }

    public void setId(@Nullable String id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        this.isActive = active;
    }
}
