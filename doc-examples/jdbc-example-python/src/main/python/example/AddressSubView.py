from dataclasses import dataclass
from typing import Annotated

from micronaut.data.annotation import Embeddable, GeneratedValue, Id, JsonSubView, MappedProperty

from example.Address import Address


@Embeddable
# tag::record-example[]
@JsonSubView(entity=Address, operations=["UPDATE", "INSERT"])
@dataclass
class AddressSubView:
    street: str
    addressID: Annotated[int | None, Id, GeneratedValue("IDENTITY"), MappedProperty("id")] = None
# end::record-example[]
