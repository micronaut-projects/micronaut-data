package example.domain;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.serde.annotation.Serdeable;

import javax.validation.constraints.NotNull;
import java.util.Collections;

@Serdeable
@MappedEntity
public record Teacher(
        @Id
        @GeneratedValue(GeneratedValue.Type.AUTO)
        Long id,
        @NotNull
        String name
) {
    public Teacher(String name) {
        this(null, name);
    }
}
