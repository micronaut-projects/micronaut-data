package example.domain.view;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.time.LocalDateTime;

@Serdeable
public record ClassView (
    Long classID,
    TeacherView teacher,
    String room,

    @JsonIgnore
    @Nullable
    LocalDateTime time
) {
}
