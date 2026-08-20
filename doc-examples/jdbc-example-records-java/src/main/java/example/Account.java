package example;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Reservable;
import jakarta.validation.constraints.PositiveOrZero;
import org.jspecify.annotations.Nullable;

// tag::reservable[]
@MappedEntity("account")
public record Account(
    @Id @GeneratedValue @Nullable Long id,
    String name,
    @Reservable @PositiveOrZero Long balance) {
}
// end::reservable[]
