from dataclasses import dataclass
from typing import Annotated

from micronaut.data.annotation import GeneratedValue, Id, MappedEntity, Relation
from org.bson.types import ObjectId

from example.Manufacturer import Manufacturer


@MappedEntity("${mapping.custom.product}")
@dataclass
class Product:
    name: str
    manufacturer: Annotated[Manufacturer | None, Relation("MANY_TO_ONE")] = None
    id: Annotated[ObjectId | None, Id, GeneratedValue] = None
