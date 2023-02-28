package example.domain.view;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ClassView (
    Long classID,
    TeacherView teacher,
    String room,
    // TODO: Represent time differently
     String time
) {
}
