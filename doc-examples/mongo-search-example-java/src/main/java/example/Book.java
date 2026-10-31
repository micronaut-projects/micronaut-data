package example;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.search.SearchField;
import io.micronaut.data.annotation.search.SearchWeight;
import io.micronaut.data.annotation.search.Searchable;

// tag::entity[]
@MappedEntity
@Searchable(language = "english") // <1>
public record Book(
    @Id
    @GeneratedValue
    String id,

    @SearchField(weight = SearchWeight.HIGH) // <2>
    String title,

    @SearchField // <3>
    String description,

    String author) {
}
// end::entity[]
