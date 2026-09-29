package example

import io.micronaut.data.annotation.Embeddable
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.JsonSubView
import io.micronaut.data.annotation.JsonView
import io.micronaut.data.annotation.MappedProperty

@Embeddable
// tag::record-example[]
@JsonSubView(entity = Address, operations = [JsonView.Operation.UPDATE, JsonView.Operation.INSERT])
class AddressSubView {
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    @MappedProperty("id")
    Long addressID
    String street
}
// end::record-example[]
