package example;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

import java.util.Date;

@MappedEntity
record Book(
        @Id @GeneratedValue @Nullable Long id,
        @DateCreated Date dateCreated,
        String title,
        int pages) {
}