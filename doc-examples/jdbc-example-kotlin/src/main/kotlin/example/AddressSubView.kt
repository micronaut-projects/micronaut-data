package example

import io.micronaut.data.annotation.Embeddable
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.JsonSubView
import io.micronaut.data.annotation.JsonView
import io.micronaut.data.annotation.MappedProperty

@Embeddable
// tag::record-example[]
@JsonSubView(entity = Address::class, operations = [JsonView.Operation.UPDATE, JsonView.Operation.INSERT])
data class AddressSubView(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.IDENTITY)
    @field:MappedProperty("id")
    val addressID: Long?,
    val street: String
)
// end::record-example[]
