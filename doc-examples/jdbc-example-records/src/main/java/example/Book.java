package example;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

@MappedEntity
record Book(
        @Id @GeneratedValue @Nullable Long id,
        String title,
        int pages) {
}