package example;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

@MappedEntity
public record CartItem(@Id @GeneratedValue @Nullable
                       Long id,
                       String name,
                       @Nullable
                       @Relation(value = Relation.Kind.MANY_TO_ONE)
                       Cart cart) {

    public CartItem(String name) {
        this(null, name, null);
    }

}
