package example;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.Version;

@MappedEntity
public record User(
        @Id @GeneratedValue @Nullable
        Long id,
        @Version
        Long version,
        String name,
        @Relation(value = Relation.Kind.ONE_TO_ONE, cascade = Relation.Cascade.ALL)
        Address address) {

    public User(String name, Address address) {
        this(null, null, name, address);
    }
}