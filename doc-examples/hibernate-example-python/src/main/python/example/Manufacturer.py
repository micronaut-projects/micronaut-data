from dataclasses import dataclass
from typing import Annotated

from jakarta.persistence import Entity, GeneratedValue, Id
from org.hibernate.annotations import BatchSize


@Entity
@BatchSize(size=10)
@dataclass
class Manufacturer:
    name: str | None = None
    id: Annotated[int | None, Id, GeneratedValue] = None
