package example.domain.view;

import com.fasterxml.jackson.annotation.JsonProperty;
import example.domain.Metadata;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.JsonView;

import java.util.List;

@JsonView("STUDENT_SCHEDULE")
public record StudentView(
    @Id
    Long studentId,
    String student,
    List<ScheduleClassView> schedule,
    @JsonProperty("_metadata")
    @Nullable
    Metadata metadata
) {
}
