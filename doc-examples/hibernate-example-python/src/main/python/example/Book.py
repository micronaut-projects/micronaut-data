from dataclasses import dataclass
from typing import Annotated

from jakarta.persistence import Entity, GeneratedValue, Id
from micronaut.serde.annotation import Serdeable


@Serdeable
@Entity
@dataclass
class Book:
    title: str | None = None
    pages: int = 0
    id: Annotated[int | None, Id, GeneratedValue] = None
