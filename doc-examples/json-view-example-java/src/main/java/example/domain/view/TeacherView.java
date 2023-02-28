package example.domain.view;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record TeacherView(
    Long teachID,
    String teacher
) {
}
