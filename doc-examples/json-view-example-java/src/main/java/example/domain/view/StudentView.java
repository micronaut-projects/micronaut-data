package example.domain.view;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.JsonDualityView;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
@MappedEntity
@JsonDualityView
public record StudentView(
    @Id
    Long studentId,
    String student,
    List<ScheduleClassView> schedule,
    @JsonProperty("_metadata")
    Metadata metadata
) {
}
