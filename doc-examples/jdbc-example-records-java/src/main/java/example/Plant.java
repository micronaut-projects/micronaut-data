package example;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Transient;

@MappedEntity
public record Plant(@Id @GeneratedValue @Nullable Long id, @Transient String name, @NonNull Integer age) {

    @Creator
    public Plant(Long id, Integer age) {
        this(id, null, age);
    }

    public Plant(Integer age) {
        this(null, null, age);
    }

}
