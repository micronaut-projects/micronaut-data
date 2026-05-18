package example;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.JsonView;
import io.micronaut.data.annotation.Relation;

import java.time.LocalDateTime;

// tag::record-example[]
@JsonView(value = "CONTACT_VIEW", alias = "cv", entity = Contact.class)
public record ContactView (
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    Long id,
    String name,
    int age,
    LocalDateTime startDateTime,
    boolean active
) {}
// end::record-example[]
