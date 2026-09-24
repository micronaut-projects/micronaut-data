from dataclasses import dataclass, field
from typing import Annotated

from micronaut.data.annotation import GeneratedValue, Id, MappedEntity
from org.bson.types import ObjectId


@MappedEntity
@dataclass
class Person:
    name: str
    age: int
    interests: list[str] = field(default_factory=list)
    id: Annotated[ObjectId | None, Id, GeneratedValue] = None
