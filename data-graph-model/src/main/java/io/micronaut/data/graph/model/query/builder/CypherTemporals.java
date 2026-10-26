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
package io.micronaut.data.graph.model.query.builder;

import io.micronaut.core.annotation.Internal;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAmount;

/**
 * Renders {@code java.time} values as Cypher temporal literals, through the temporal function that constructs the
 * matching Cypher type from its ISO-8601 text: {@code date('2024-01-31')}, {@code datetime('2024-01-31T10:15:30Z')},
 * {@code localdatetime('2024-01-31T10:15:30')}, {@code time('10:15:30+01:00')}, {@code localtime('10:15:30')}
 * and {@code duration('PT1H')}. The ISO-8601 text of the Java types contains no quotes, so it needs no escaping.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
final class CypherTemporals {

    private CypherTemporals() {
    }

    /**
     * Renders a temporal value. A value Cypher has no type for, such as a {@link java.time.YearMonth}, stays a string.
     *
     * @param value The value
     * @return The Cypher literal
     */
    static String literal(Temporal value) {
        String function = switch (value) {
            case LocalDate ignored -> "date";
            case LocalDateTime ignored -> "localdatetime";
            case LocalTime ignored -> "localtime";
            case OffsetTime ignored -> "time";
            case ZonedDateTime ignored -> "datetime";
            case OffsetDateTime ignored -> "datetime";
            case Instant ignored -> "datetime";
            default -> null;
        };
        return function == null ? "'" + value + "'" : function + "('" + value + "')";
    }

    /**
     * Renders a duration or a period.
     *
     * @param value The value
     * @return The Cypher literal
     */
    static String literal(TemporalAmount value) {
        return "duration('" + value + "')";
    }
}
