/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.data.annotation.search;

import io.micronaut.core.annotation.Experimental;

/**
 * Relative weight of a {@link SearchField} in the relevance score.
 *
 * <p>Backends map the four levels to their own scale: PostgreSQL weight labels {@code A} to
 * {@code D}, MongoDB index weights, Elasticsearch boosts.</p>
 *
 * @since 5.4.0
 */
@Experimental
public enum SearchWeight {
    /**
     * The field counts most, for example a title. PostgreSQL label {@code A}.
     */
    HIGH,
    /**
     * The default weight. PostgreSQL label {@code B} when declared explicitly; a field without a
     * declared weight is indexed without a label.
     */
    MEDIUM,
    /**
     * A field that counts less, for example a description. PostgreSQL label {@code C}.
     */
    LOW,
    /**
     * A field that barely counts, for example a body of text. PostgreSQL label {@code D}.
     */
    LOWEST;

    /**
     * @return The PostgreSQL {@code setweight} label for this weight
     */
    public char postgresLabel() {
        return switch (this) {
            case HIGH -> 'A';
            case MEDIUM -> 'B';
            case LOW -> 'C';
            case LOWEST -> 'D';
        };
    }

    /**
     * @return A numeric weight usable as an index weight or query boost, {@code 10} for the highest
     */
    public int numericWeight() {
        return switch (this) {
            case HIGH -> 10;
            case MEDIUM -> 5;
            case LOW -> 2;
            case LOWEST -> 1;
        };
    }
}
