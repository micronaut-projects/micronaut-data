# tag::upsert-entity[]
from dataclasses import dataclass
from typing import Annotated

from micronaut.data.annotation import Id, MappedEntity


@MappedEntity
@dataclass
class Flight:
    number: Annotated[str, Id]
    origin: str | None = None
    destination: str | None = None
# end::upsert-entity[]
