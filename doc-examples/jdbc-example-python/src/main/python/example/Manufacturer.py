from dataclasses import dataclass
from typing import Annotated

from micronaut.data.annotation import GeneratedValue, Id, MappedEntity


@MappedEntity
@dataclass
class Manufacturer:
    name: str
    id: Annotated[int | None, Id, GeneratedValue] = None
