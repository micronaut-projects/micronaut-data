package example.domain.view;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ScheduleClassView(
    @JsonProperty("class")
    ClassView clazz,

    Long id
) {
}
