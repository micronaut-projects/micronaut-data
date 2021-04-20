package example;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

import java.util.List;

@MappedEntity
public record Cart(@Id @GeneratedValue @Nullable
                   Long id,
                   @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "cart", cascade = Relation.Cascade.ALL)
                   List<CartItem> items) {

    public Cart(List<CartItem> items) {
        this(null, items);
    }

}
