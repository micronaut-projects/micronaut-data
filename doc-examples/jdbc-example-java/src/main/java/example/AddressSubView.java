package example;

import io.micronaut.data.annotation.*;

@Embeddable
// tag::record-example[]
@JsonSubView(entity = Address.class, operations = { JsonView.Operation.UPDATE, JsonView.Operation.INSERT })
public record AddressSubView(
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    @MappedProperty("id")
    Long addressID,
    String street
) {}
// end::record-example[]
