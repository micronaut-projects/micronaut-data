# tag::upsert-entity[]
from dataclasses import dataclass
from typing import Annotated

from micronaut.data.annotation import GeneratedValue, Id, Index, MappedEntity


@MappedEntity
@Index(columns=["email"], unique=True)
@dataclass
class Passenger:
    email: str
    firstName: str | None = None
    lastName: str | None = None
    id: Annotated[int | None, Id, GeneratedValue("IDENTITY")] = None
# end::upsert-entity[]
