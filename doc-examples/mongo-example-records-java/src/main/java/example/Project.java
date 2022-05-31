
package example;

import io.micronaut.data.annotation.EmbeddedId;
import io.micronaut.data.annotation.MappedEntity;

@MappedEntity
public record Project(
        @EmbeddedId ProjectId projectId,
        String name) {
}

