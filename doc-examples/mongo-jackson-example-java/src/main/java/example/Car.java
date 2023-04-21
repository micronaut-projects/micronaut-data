package example;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

import java.util.List;

@MappedEntity
public record Car(@Id Long id,
                  String name,
                  @Nullable @Relation(Relation.Kind.ONE_TO_ONE) CarManufacturer1 manufacturerOneToOne,
                  @Nullable @Relation(Relation.Kind.MANY_TO_ONE) CarManufacturer2 manufacturerManyToOne,
                  @Nullable @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "car") List<CarManufacturer3> manufacturersOneToMany,
                  @Nullable @Relation(value = Relation.Kind.MANY_TO_MANY, mappedBy = "cars") List<CarManufacturer4> manufacturersManyToMany) {
}
