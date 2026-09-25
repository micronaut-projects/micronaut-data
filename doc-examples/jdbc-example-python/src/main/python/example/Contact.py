from dataclasses import dataclass
from typing import Annotated

from java.time import LocalDateTime
from micronaut.data.annotation import GeneratedValue, Id, MappedEntity


@MappedEntity(value="TBL_CONTACT", alias="c")
@dataclass
class Contact:
    name: str
    age: int
    active: bool | None = None
    startDateTime: LocalDateTime | None = None
    id: Annotated[int | None, Id, GeneratedValue("IDENTITY")] = None
