# tag::student[]
from dataclasses import dataclass
from typing import Annotated

from jakarta.persistence import Entity, GeneratedValue, Id, Version


@Entity
@dataclass
class Student:
    id: Annotated[int | None, Id, GeneratedValue] = None
    version: Annotated[int | None, Version] = None
    # end::student[]
    name: str | None = None
