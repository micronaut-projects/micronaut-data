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
package io.micronaut.data.graph.runtime.age;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.graph.runtime.GraphValueEncoders;
import io.micronaut.json.JsonMapper;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAmount;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

/**
 * Writes the values of Apache AGE queries: the parameter map of a {@code cypher()} call is JSON, which AGE reads as
 * an {@code agtype} map.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public final class AgeParameters {

    private static final Argument<Map<String, Object>> PARAMETERS = Argument.mapOf(String.class, Object.class);

    private AgeParameters() {
    }

    /**
     * Registers the encoders of values that agtype has no type for: temporal values are written as ISO-8601 strings
     * and bytes as Base64 strings.
     *
     * @param encoders The encoders
     */
    public static void registerEncoders(GraphValueEncoders encoders) {
        encoders.register(Temporal.class, (value, e) -> value.toString())
            .register(TemporalAmount.class, (value, e) -> value.toString())
            .register(Instant.class, (value, e) -> value.toString())
            .register(java.sql.Date.class, (value, e) -> value.toLocalDate().toString())
            .register(Date.class, (value, e) -> value.toInstant().atOffset(ZoneOffset.UTC).toString())
            .register(byte[].class, (value, e) -> Base64.getEncoder().encodeToString(value));
    }

    /**
     * Writes the parameter map of a statement as JSON. The values are already encoded: strings, numbers, booleans,
     * lists and maps.
     *
     * @param jsonMapper The JSON mapper of Micronaut Serialization
     * @param parameters The parameters
     * @return The JSON text
     */
    public static String write(JsonMapper jsonMapper, Map<String, Object> parameters) {
        try {
            return jsonMapper.writeValueAsString(PARAMETERS, parameters);
        } catch (IOException e) {
            throw new DataAccessException("Cannot write the parameters of an Apache AGE query as JSON: " + e.getMessage(), e);
        }
    }
}
