package example.domain.view;

import com.fasterxml.jackson.annotation.JsonProperty;
import example.Metadata;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.model.JsonDataObject;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
@MappedEntity
public record StudentView (
    @Id
    Long studentId,
    String student,
    List<ScheduleClassView> schedule,
    @JsonProperty("_metadata")
    Metadata metadata
)  implements JsonDataObject {
}
