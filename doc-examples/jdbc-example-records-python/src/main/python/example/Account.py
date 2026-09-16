# tag::reservable[]
from dataclasses import dataclass
from typing import Annotated

from jakarta.validation.constraints import PositiveOrZero
from micronaut.data.annotation import GeneratedValue, Id, MappedEntity
from micronaut.data.annotation import Reservable


@MappedEntity("account")
@dataclass(frozen=True)
class Account:
    id: Annotated[int | None, Id, GeneratedValue]
    name: str
    balance: Annotated[int, Reservable, PositiveOrZero]
# end::reservable[]
