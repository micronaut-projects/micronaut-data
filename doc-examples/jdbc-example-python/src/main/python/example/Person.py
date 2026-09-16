from dataclasses import dataclass
from typing import Annotated

from micronaut.data.annotation import GeneratedValue, Id, MappedEntity, MappedProperty


@MappedEntity
@dataclass
class Person:
    name: str
    age: int
    longName: Annotated[str | None, MappedProperty(value="long_name_column_legacy_system", alias="long_name")] = None
    id: Annotated[int | None, Id, GeneratedValue] = None
