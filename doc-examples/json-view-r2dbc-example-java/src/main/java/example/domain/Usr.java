package example.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.serde.annotation.Serdeable;

import java.sql.Blob;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.Date;

@Serdeable
@MappedEntity
public record Usr(
        @Id
        Long id,
        String name,

        Period ym,
        Duration ds,
        Double bd,

        @MappedProperty("FDATE")
        Date date,

        @MappedProperty("tstz")
        OffsetDateTime offsetDateTime,

        @MappedProperty("TS")
        Instant instant,

        @JsonIgnore
        Blob memo
) {}
