from dataclasses import dataclass
from typing import Annotated

from micronaut.data.annotation import GeneratedValue, Id, Index, MappedEntity, Srid
from micronaut.data.model.geo import Point


@MappedEntity
@dataclass(frozen=True)
class Library:
    id: Annotated[int | None, Id, GeneratedValue]
    name: str
    email: str
    capacity: int
    location: Annotated[Point, Srid(4326), Index(columns="location")]
