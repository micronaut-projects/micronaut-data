from dataclasses import dataclass
from typing import Annotated

from micronaut.data.annotation import GeneratedValue, Id, MappedEntity
from micronaut.serde.annotation import Serdeable


@Serdeable
@MappedEntity
@dataclass
class Author:
    name: str
    id: Annotated[int | None, Id, GeneratedValue] = None
