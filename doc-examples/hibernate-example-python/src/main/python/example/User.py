from dataclasses import dataclass
from typing import Annotated

from jakarta.persistence import Entity, GeneratedValue, Id, Table
from micronaut.data.annotation import Where


@Entity(name="Users")
@Table(name="USERS")
@Where("@.enabled = true")
@dataclass
class User:
    name: str | None = None
    enabled: bool = True
    id: Annotated[int | None, Id, GeneratedValue] = None
