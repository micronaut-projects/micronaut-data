package io.micronaut.data.neo4j.features;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.graph.annotation.Node;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.UUID;

@Node
public record Event(@Id String name,
                    @Nullable LocalDate day,
                    @Nullable LocalDateTime start,
                    @Nullable Instant created,
                    @Nullable ZonedDateTime zoned,
                    @Nullable OffsetDateTime offset,
                    @Nullable Date legacy,
                    @Nullable Duration length,
                    @Nullable UUID ref,
                    @Nullable Priority priority,
                    @Nullable BigDecimal price,
                    @Nullable @MappedProperty(converter = PointsConverter.class) Points points) {

    public enum Priority { LOW, HIGH }

    public record Points(int value) {
    }
}
