package example;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

@MappedEntity
public record User(
        @Id @GeneratedValue @Nullable
        Long id,
        String name,
        @Relation(value = Relation.Kind.ONE_TO_ONE, cascade = Relation.Cascade.ALL)
        Address address) {

    public User(String name, Address address) {
        this(null, name, address);
    }
}