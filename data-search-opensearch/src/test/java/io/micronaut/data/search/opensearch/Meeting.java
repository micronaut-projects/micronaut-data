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
package io.micronaut.data.search.opensearch;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.search.SearchField;
import io.micronaut.data.annotation.search.Searchable;
import io.micronaut.serde.annotation.Serdeable;
import org.jspecify.annotations.Nullable;

import java.time.ZonedDateTime;
import java.util.Date;

/**
 * An entity with a date, a temporal and an enum with custom serialised names, which the derived
 * finders must bind in the form the JSON mapper stores them in.
 *
 * @param id The id
 * @param title The title
 * @param createdAt The creation date
 * @param startsAt The start time
 * @param priority The priority
 */
@MappedEntity("meetings")
@Searchable
@Serdeable
public record Meeting(@Id @Nullable String id,
                      @SearchField String title,
                      Date createdAt,
                      ZonedDateTime startsAt,
                      Priority priority) {

    /**
     * A priority serialised by its code rather than its name.
     */
    @Serdeable
    public enum Priority {
        HIGH("urgent"),
        LOW("routine");

        private final String code;

        Priority(String code) {
            this.code = code;
        }

        @JsonValue
        public String getCode() {
            return code;
        }

        @JsonCreator
        public static Priority fromCode(String code) {
            for (Priority priority : values()) {
                if (priority.code.equals(code)) {
                    return priority;
                }
            }
            throw new IllegalArgumentException("Unknown priority: " + code);
        }
    }
}
