package example;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.graph.annotation.Node;

import java.time.LocalDate;

@Node("Movie")
public record RecordMovie(@Id String title,
                          @MappedProperty("tagline") String description,
                          Integer released,
                          Long votes,
                          Double rating,
                          Boolean colour,
                          String director,
                          String studio,
                          Long budget,
                          LocalDate premiere) {
}
