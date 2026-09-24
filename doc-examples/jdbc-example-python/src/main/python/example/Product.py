from dataclasses import dataclass
from typing import Annotated

from micronaut.data.annotation import GeneratedValue, Id, MappedEntity, Relation

from example.Manufacturer import Manufacturer


@MappedEntity
@dataclass
class Product:
    name: str
    manufacturer: Annotated[Manufacturer | None, Relation(value="MANY_TO_ONE")]
    id: Annotated[int | None, Id, GeneratedValue] = None
