package example.domain;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.serde.annotation.Serdeable;

import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

@Serdeable
@MappedEntity
public record Class(
        @Id
        @GeneratedValue(GeneratedValue.Type.AUTO)
        Long id,
        @NotNull
        String name,
        @NotNull
        String room,
        @NotNull
        LocalTime time,
        @NotNull
        @Relation(Relation.Kind.MANY_TO_ONE)
        Teacher teacher) {
    public Class(String name, String room, LocalTime time, Teacher teacher) {
        this(null, name, room, time, teacher);
    }
}
