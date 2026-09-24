from dataclasses import dataclass
from typing import Annotated

from micronaut.data.annotation import GeneratedValue, Id, MappedEntity


@MappedEntity(value="TBL_ADDRESS", alias="a")
@dataclass
class Address:
    street: str
    city: str
    id: Annotated[int | None, Id, GeneratedValue("IDENTITY")] = None
