/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.data.runtime.intercept;

import io.micronaut.context.annotation.Context;
import io.micronaut.core.convert.ConversionService;

import java.sql.Timestamp;
import java.time.*;
import java.time.chrono.ChronoLocalDate;
import java.util.Date;

/**
 * Internal Data initialization.
 *
 * @author graemerocher
 * @since 1.0
 */
@Context
class DataInitializer {

    /**
     * Default constructor.
     */
    DataInitializer() {
        ConversionService<?> conversionService = ConversionService.SHARED;
        conversionService.addConverter(OffsetDateTime.class, java.sql.Date.class, offsetDateTime ->
                new java.sql.Date(offsetDateTime.toInstant().toEpochMilli())
        );
        conversionService.addConverter(Timestamp.class, ZonedDateTime.class, timestamp ->
                timestamp.toLocalDateTime().atZone(ZoneId.systemDefault())
        );
        conversionService.addConverter(ZonedDateTime.class, Timestamp.class, zonedDateTime ->
                new Timestamp(zonedDateTime.toInstant().toEpochMilli())
        );
        conversionService.addConverter(Timestamp.class, LocalDateTime.class, Timestamp::toLocalDateTime);
        conversionService.addConverter(Instant.class, Date.class, instant ->
                new Date(instant.toEpochMilli())
        );
        conversionService.addConverter(LocalDateTime.class, Date.class, localDateTime ->
                new Date(localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
        );
        conversionService.addConverter(Date.class, LocalDateTime.class, date ->
                Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDateTime()
        );
        conversionService.addConverter(Date.class, OffsetDateTime.class, date ->
                Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toOffsetDateTime()
        );
        conversionService.addConverter(Date.class, LocalDate.class, date ->
                Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDate()
        );
        conversionService.addConverter(Date.class, Instant.class, date ->
                Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toInstant()
        );
        conversionService.addConverter(ChronoLocalDate.class, Date.class, localDate ->
                new Date(localDate.atTime(LocalTime.MIDNIGHT).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
        );
        conversionService.addConverter(OffsetDateTime.class, Date.class, offsetDateTime ->
                new Date(offsetDateTime.toInstant().toEpochMilli())
        );
        conversionService.addConverter(OffsetDateTime.class, Instant.class, OffsetDateTime::toInstant);
        conversionService.addConverter(OffsetDateTime.class, Long.class, offsetDateTime ->
                offsetDateTime.toInstant().toEpochMilli()
        );
        conversionService.addConverter(OffsetDateTime.class, Timestamp.class, offsetDateTime ->
                new Timestamp(offsetDateTime.toInstant().toEpochMilli())
        );
        conversionService.addConverter(OffsetDateTime.class, LocalDateTime.class, OffsetDateTime::toLocalDateTime);
        conversionService.addConverter(OffsetDateTime.class, LocalDate.class, OffsetDateTime::toLocalDate);
    }
}
