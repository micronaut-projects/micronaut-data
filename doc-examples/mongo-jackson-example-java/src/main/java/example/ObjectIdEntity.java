package example;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import org.bson.types.ObjectId;

@MappedEntity
public record ObjectIdEntity(@Id ObjectId id) {
}
