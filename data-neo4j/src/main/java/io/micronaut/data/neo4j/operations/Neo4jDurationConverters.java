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
package io.micronaut.data.neo4j.operations;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.MutableConversionService;
import io.micronaut.core.convert.TypeConverterRegistrar;
import io.micronaut.data.exceptions.DataAccessException;
import org.neo4j.driver.types.IsoDuration;

import java.time.Duration;
import java.time.Period;

/**
 * Rejects reading a Neo4j duration that {@link Neo4jValues} left as a driver value, because it has both a calendar part
 * and a time part, into a type that cannot represent it exactly.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Prototype
@Internal
final class Neo4jDurationConverters implements TypeConverterRegistrar {

    @Override
    public void register(MutableConversionService conversionService) {
        conversionService.addConverter(IsoDuration.class, Duration.class, Neo4jDurationConverters::reject);
        conversionService.addConverter(IsoDuration.class, Period.class, Neo4jDurationConverters::reject);
    }

    private static <T> T reject(IsoDuration duration) {
        throw new DataAccessException("Cannot read the Neo4j duration [" + duration + "]: it has both a calendar part (months or days) and a time part, which no java.time type represents exactly. Return it as a string with toString(), or return its components");
    }
}
