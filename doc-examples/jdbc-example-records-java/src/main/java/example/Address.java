package example;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

@MappedEntity
public record Address(
        @Id @GeneratedValue @Nullable
        Long id,
        String street,
        @Relation(value = Relation.Kind.MANY_TO_ONE, cascade = Relation.Cascade.ALL)
        City city,
        @Nullable
        @Relation(value = Relation.Kind.ONE_TO_ONE, mappedBy = "address", cascade = Relation.Cascade.ALL)
        User user) {

    public Address(String street, City city) {
        this(null, street, city, null);
    }
}