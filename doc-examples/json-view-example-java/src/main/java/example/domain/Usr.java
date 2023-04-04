package example.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import example.repository.DsIntervalToDurationConverter;
import example.repository.YmIntervalToPeriodConverter;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;

import java.sql.Blob;
import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.util.Date;

@Serdeable
@MappedEntity
public record Usr(
        @Id
        Long id,
        String name,

        @TypeDef(type = DataType.OBJECT, converter = YmIntervalToPeriodConverter.class)
        Period ym,
        @TypeDef(type = DataType.OBJECT, converter = DsIntervalToDurationConverter.class)
        Duration ds,
        Double bd,

        @MappedProperty("FDATE")
        Date date,

       /* @MappedProperty("tstz")
        OffsetDateTime offsetDateTime, */

        @MappedProperty("TS")
        Instant instant,

        @JsonIgnore
        Blob memo
) {}
