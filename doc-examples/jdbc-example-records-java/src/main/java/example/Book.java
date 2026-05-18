package example;

import org.jspecify.annotations.Nullable;
import io.micronaut.data.annotation.*;
import java.util.Date;

@MappedEntity
public// <1>
record Book(
        @Id @GeneratedValue @Nullable Long id, // <2>
        @DateCreated @Nullable Date dateCreated,
        String title,
        int pages) {
}
