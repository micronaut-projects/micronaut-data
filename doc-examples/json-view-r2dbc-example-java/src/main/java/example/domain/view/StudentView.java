package example.domain.view;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import example.domain.Metadata;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.model.JsonDataObject;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
@MappedEntity
public record StudentView(
    @Id
    Long studentId,
    String student,
    List<ScheduleClassView> schedule,
    //@JsonProperty("_metadata")
    @JsonIgnore
    @Nullable
    Metadata metadata
) implements JsonDataObject {
}
