from dataclasses import dataclass
from typing import Annotated

from micronaut.data.annotation import GeneratedValue, Id, MappedEntity
from org.bson.types import ObjectId


@MappedEntity("${mapping.custom.manufacturer}")
@dataclass
class Manufacturer:
    name: str
    id: Annotated[ObjectId | None, Id, GeneratedValue] = None
