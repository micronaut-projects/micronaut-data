package example.domain.view;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ClassView (
    Long classID,
    TeacherView teacher,
    String room,
    // TODO: Represent time differently
    @JsonIgnore
    @Nullable
    String time
) {
}
