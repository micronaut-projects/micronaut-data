from dataclasses import dataclass
from typing import Annotated

from java.time import LocalDateTime
from micronaut.data.annotation import GeneratedValue, Id, JsonView

from example.Contact import Contact


# tag::record-example[]
@JsonView(value="CONTACT_VIEW", alias="cv", entity=Contact)
@dataclass
class ContactView:
    name: str
    age: int
    startDateTime: LocalDateTime | None = None
    active: bool = False
    id: Annotated[int | None, Id, GeneratedValue("IDENTITY")] = None
# end::record-example[]
