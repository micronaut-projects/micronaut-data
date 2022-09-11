/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.data.tck.entities;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;

import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

@MappedEntity("timezone_basic_types")
public class TimezoneBasicTypes {
    @Id
    @GeneratedValue
    private Long myId;

    private LocalDateTime localDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
    private ZonedDateTime zonedDateTime = ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS);
    @MappedProperty(definition = "timestamp with time zone")
    private ZonedDateTime zonedDateTimeWithTimezone = ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS);
    private OffsetDateTime offsetDateTime = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS);
    @MappedProperty(definition = "timestamp with time zone")
    private OffsetDateTime offsetDateTimeWithTimezone = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS);
    private LocalDate localDate = LocalDate.now();
    private LocalTime localTime = LocalTime.now().truncatedTo(ChronoUnit.MILLIS);
    private Instant instant = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    @MappedProperty(definition = "timestamp with time zone")
    private Instant instantWithTimezone = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    private Timestamp timestamp = Timestamp.from(Instant.now().truncatedTo(ChronoUnit.MILLIS));
    @MappedProperty(definition = "timestamp with time zone")
    private Timestamp timestampWithTimezone = Timestamp.from(Instant.now().truncatedTo(ChronoUnit.MILLIS));

    private Time time = Time.valueOf(LocalTime.now());

    public Long getMyId() {
        return myId;
    }

    public void setMyId(Long myId) {
        this.myId = myId;
    }

    public LocalDateTime getLocalDateTime() {
        return localDateTime;
    }

    public void setLocalDateTime(LocalDateTime localDateTime) {
        this.localDateTime = localDateTime;
    }

    public ZonedDateTime getZonedDateTime() {
        return zonedDateTime;
    }

    public void setZonedDateTime(ZonedDateTime zonedDateTime) {
        this.zonedDateTime = zonedDateTime;
    }

    public ZonedDateTime getZonedDateTimeWithTimezone() {
        return zonedDateTimeWithTimezone;
    }

    public void setZonedDateTimeWithTimezone(ZonedDateTime zonedDateTimeWithTimezone) {
        this.zonedDateTimeWithTimezone = zonedDateTimeWithTimezone;
    }

    public OffsetDateTime getOffsetDateTime() {
        return offsetDateTime;
    }

    public void setOffsetDateTime(OffsetDateTime offsetDateTime) {
        this.offsetDateTime = offsetDateTime;
    }

    public OffsetDateTime getOffsetDateTimeWithTimezone() {
        return offsetDateTimeWithTimezone;
    }

    public void setOffsetDateTimeWithTimezone(OffsetDateTime offsetDateTimeWithTimezone) {
        this.offsetDateTimeWithTimezone = offsetDateTimeWithTimezone;
    }

    public LocalDate getLocalDate() {
        return localDate;
    }

    public void setLocalDate(LocalDate localDate) {
        this.localDate = localDate;
    }

    public LocalTime getLocalTime() {
        return localTime;
    }

    public void setLocalTime(LocalTime localTime) {
        this.localTime = localTime;
    }

    public Instant getInstant() {
        return instant;
    }

    public void setInstant(Instant instant) {
        this.instant = instant;
    }

    public Instant getInstantWithTimezone() {
        return instantWithTimezone;
    }

    public void setInstantWithTimezone(Instant instantWithTimezone) {
        this.instantWithTimezone = instantWithTimezone;
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }

    public Timestamp getTimestampWithTimezone() {
        return timestampWithTimezone;
    }

    public void setTimestampWithTimezone(Timestamp timestampWithTimezone) {
        this.timestampWithTimezone = timestampWithTimezone;
    }

    public Time getTime() {
        return time;
    }

    public void setTime(Time time) {
        this.time = time;
    }
}
