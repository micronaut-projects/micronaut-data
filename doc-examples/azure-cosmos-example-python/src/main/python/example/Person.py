from dataclasses import dataclass
from typing import Annotated

from micronaut.data.annotation import GeneratedValue, Id, MappedEntity


@MappedEntity
@dataclass
class Person:
    name: str
    age: int
    id: Annotated[str | None, Id, GeneratedValue] = None
